package com.violet.safe.ui.device;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.scottyab.rootbeer.RootBeer;
import com.violet.safe.R;
import com.violet.safe.core.util.BatterySysFiles;
import com.violet.safe.core.util.CpuSysFiles;
import com.violet.safe.core.util.GpuInfoQuery;
import com.violet.safe.core.util.SelinuxShellUtil;
import com.violet.safe.core.util.SelinuxStatusReader;
import com.violet.safe.ui.widget.CpuRingGaugeView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceFragment extends Fragment {

    private static final int REQ_READ_PHONE = 6011;

    /**
     * 部分 {@code android.jar} 存根未声明 {@link BatteryManager} 的较新 {@code BATTERY_PROPERTY_*} 字段，编译期用反射解析一次。
     */
    private static final int BATTERY_PROPERTY_CHARGE_FULL_NUM = batteryPropertyId("BATTERY_PROPERTY_CHARGE_FULL", 6);
    private static final int BATTERY_PROPERTY_CHARGE_FULL_DESIGN_NUM =
            batteryPropertyId("BATTERY_PROPERTY_CHARGE_FULL_DESIGN", 37);
    private static final int BATTERY_PROPERTY_CYCLE_COUNT_NUM = batteryPropertyId("BATTERY_PROPERTY_CYCLE_COUNT", 35);

    private static int batteryPropertyId(String fieldName, int fallbackIfMissing) {
        try {
            return BatteryManager.class.getField(fieldName).getInt(null);
        } catch (Throwable ignored) {
            return fallbackIfMissing;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 各核圆环、存储占用、电池瞬时量：每秒刷新（部分机型电量广播极稀疏，仅靠广播电压/电流不更新） */
    private final Runnable cpuRingTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            refreshBatteryStickyUi();
            refreshCpuCoreRingsUi();
            refreshStorageUi();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                return;
            }
            applyBatteryIntent(intent);
        }
    };

    private ExecutorService gpuExecutor;

    private GridLayout llCpuCoreRings;
    private final List<CpuCoreRingHolder> cpuCoreRingHolders = new ArrayList<>();

    private TextView tvBatteryStatusSummary;
    private TextView tvBatteryHealth;
    private TextView tvBatteryDesignMah;
    private TextView tvBatteryCycles;
    private TextView tvBatteryTemp;
    private TextView tvBatteryCurrent;
    private TextView tvBatteryVoltage;

    private TextView tvStorageUsedTotal;
    private ProgressBar pbStorage;

    private TextView tvGpuRenderer;
    private TextView tvGpuApi;
    private TextView tvAndroidId;

    private TextView tvImei;
    private TextView tvImsi;
    private TextView tvSelinuxStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        llCpuCoreRings = view.findViewById(R.id.llCpuCoreRings);
        inflateCpuCoreRingsIfNeeded();

        TextView tvDeviceName = view.findViewById(R.id.tvDeviceName);
        TextView tvDeviceCode = view.findViewById(R.id.tvDeviceCode);
        TextView tvAndroidVersion = view.findViewById(R.id.tvAndroidVersion);
        TextView tvKernelVersion = view.findViewById(R.id.tvKernelVersion);
        tvSelinuxStatus = view.findViewById(R.id.tvSelinuxStatus);
        TextView tvBootSlot = view.findViewById(R.id.tvBootSlot);
        TextView tvDeviceRootBadge = view.findViewById(R.id.tvDeviceRootBadge);
        View badgeDeviceRoot = view.findViewById(R.id.badgeDeviceRoot);

        TextView tvRadioVersion = view.findViewById(R.id.tvRadioVersion);
        TextView tvBuildDisplay = view.findViewById(R.id.tvBuildDisplay);
        TextView tvSecurityPatch = view.findViewById(R.id.tvSecurityPatch);
        tvAndroidId = view.findViewById(R.id.tvAndroidId);
        tvImei = view.findViewById(R.id.tvImei);
        tvImsi = view.findViewById(R.id.tvImsi);

        tvBatteryStatusSummary = view.findViewById(R.id.tvBatteryStatusSummary);
        tvBatteryHealth = view.findViewById(R.id.tvBatteryHealth);
        tvBatteryDesignMah = view.findViewById(R.id.tvBatteryDesignMah);
        tvBatteryCycles = view.findViewById(R.id.tvBatteryCycles);
        tvBatteryTemp = view.findViewById(R.id.tvBatteryTemp);
        tvBatteryCurrent = view.findViewById(R.id.tvBatteryCurrent);
        tvBatteryVoltage = view.findViewById(R.id.tvBatteryVoltage);

        tvStorageUsedTotal = view.findViewById(R.id.tvStorageUsedTotal);
        pbStorage = view.findViewById(R.id.pbStorage);
        tvGpuRenderer = view.findViewById(R.id.tvGpuRenderer);
        tvGpuApi = view.findViewById(R.id.tvGpuApi);

        gpuExecutor = Executors.newSingleThreadExecutor();

        String deviceTitle = Build.MANUFACTURER + " " + Build.MODEL;
        tvDeviceName.setText(deviceTitle);
        tvDeviceCode.setText("设备代号: " + Build.DEVICE);

        tvAndroidVersion.setText("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        String kernelVersion = System.getProperty("os.version");
        if (kernelVersion == null) {
            kernelVersion = "未知";
        }
        tvKernelVersion.setText(kernelVersion);

        applySelinuxSummary();

        tvBootSlot.setText(formatSlotShort(getBootSlotRaw()));

        RootBeer rootBeer = new RootBeer(requireContext());
        boolean rooted = rootBeer.isRooted();
        if (tvDeviceRootBadge != null) {
            tvDeviceRootBadge.setText(rooted ? "已 Root" : "未 Root");
        }
        if (badgeDeviceRoot != null) {
            badgeDeviceRoot.setVisibility(View.VISIBLE);
        }

        if (tvRadioVersion != null) {
            String radio = Build.getRadioVersion();
            tvRadioVersion.setText(TextUtils.isEmpty(radio) ? "—" : radio);
        }
        if (tvBuildDisplay != null) {
            tvBuildDisplay.setText(Build.DISPLAY);
        }
        if (tvSecurityPatch != null) {
            tvSecurityPatch.setText(Build.VERSION.SECURITY_PATCH);
        }
        refreshAndroidIdUi();
        refreshPhoneIdentifiers();

        bindGpuSectionAsync();

        requestPhonePermissionIfNeeded();

        refreshStorageUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sticky = requireContext().registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                sticky = requireContext().registerReceiver(null, filter);
            }
        } catch (Exception ignored) {
        }
        if (sticky != null) {
            applyBatteryIntent(sticky);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(batteryReceiver, filter);
            }
        } catch (Exception ignored) {
        }

        if (getContext() != null
                && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            refreshPhoneIdentifiers();
        }

        applySelinuxSummary();
        refreshAndroidIdUi();

        mainHandler.removeCallbacks(cpuRingTickRunnable);
        mainHandler.post(cpuRingTickRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        mainHandler.removeCallbacks(cpuRingTickRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (gpuExecutor != null) {
            gpuExecutor.shutdownNow();
            gpuExecutor = null;
        }
        llCpuCoreRings = null;
        cpuCoreRingHolders.clear();
        tvBatteryStatusSummary = null;
        tvBatteryHealth = null;
        tvBatteryDesignMah = null;
        tvBatteryCycles = null;
        tvBatteryTemp = null;
        tvBatteryCurrent = null;
        tvBatteryVoltage = null;
        tvStorageUsedTotal = null;
        pbStorage = null;
        tvGpuRenderer = null;
        tvGpuApi = null;
        tvImei = null;
        tvImsi = null;
        tvSelinuxStatus = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_PHONE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 系统回调时 Fragment 可能尚未完全 attach；延后到下一帧再读卡信息，避免 requireContext 抛错闪退
            mainHandler.post(() -> {
                if (isAdded()) {
                    refreshPhoneIdentifiers();
                }
            });
        }
    }

    private void requestPhonePermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_READ_PHONE);
        }
    }

    private void refreshPhoneIdentifiers() {
        if (tvImei != null) {
            tvImei.setText(readImeiOrPlaceholder());
        }
        if (tvImsi != null) {
            tvImsi.setText(readImsiOrPlaceholder());
        }
    }

    private void refreshAndroidIdUi() {
        if (tvAndroidId == null || getContext() == null) {
            return;
        }
        String viaSu = readAndroidIdViaSu();
        if (!TextUtils.isEmpty(viaSu)) {
            tvAndroidId.setText(viaSu);
            return;
        }
        String apiValue = Settings.Secure.getString(requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        tvAndroidId.setText(!TextUtils.isEmpty(apiValue) ? apiValue : "—");
    }

    private String readAndroidIdViaSu() {
        SelinuxShellUtil.ShellResult result = SelinuxShellUtil.runSu("settings get secure android_id", 1500);
        if (!result.success) {
            return "";
        }
        String out = result.stdout == null ? "" : result.stdout.trim();
        if (TextUtils.isEmpty(out) || "null".equalsIgnoreCase(out)) {
            return "";
        }
        return out;
    }

    private void applyBatteryIntent(Intent batteryStatus) {
        if (batteryStatus == null || getContext() == null) {
            return;
        }
        try {
            applyBatteryIntentInner(batteryStatus);
        } catch (Throwable ignored) {
            // 部分机型在电量属性 / 广播字段上实现不完整，避免从权限页返回后 onResume 里直接崩进程
        }
    }

    /**
     * 不注册 Receiver，仅取系统缓存的 {@link Intent#ACTION_BATTERY_CHANGED}；配合定时任务可更新电压/电流。
     */
    private void refreshBatteryStickyUi() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent sticky;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sticky = ctx.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                sticky = ctx.registerReceiver(null, filter);
            }
            if (sticky != null) {
                applyBatteryIntent(sticky);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyBatteryIntentInner(Intent batteryStatus) {
        if (tvBatteryStatusSummary != null) {
            tvBatteryStatusSummary.setText(formatBatteryStatusSummary(batteryStatus));
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
        if (tvBatteryHealth != null) {
            if (pct >= 0) {
                tvBatteryHealth.setText(pct + "%");
            } else {
                tvBatteryHealth.setText("—");
            }
        }

        if (tvBatteryTemp != null) {
            int t = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", t / 10f));
        }

        if (tvBatteryVoltage != null) {
            long sysUv = BatterySysFiles.readVoltageNowMicroVolts();
            if (sysUv != Long.MIN_VALUE) {
                tvBatteryVoltage.setText(sysfsMicroVoltsToMilliVolts(sysUv) + " mV");
            } else {
                int v = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                if (v > 0) {
                    tvBatteryVoltage.setText(v + " mV");
                } else {
                    tvBatteryVoltage.setText("—");
                }
            }
        }

        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);

        if (tvBatteryCurrent != null) {
            String curLabel = formatBatteryCurrentMa(bm);
            tvBatteryCurrent.setText(curLabel != null ? curLabel : "—");
        }

        if (tvBatteryDesignMah != null) {
            String design = formatDesignCapacityMah(bm);
            tvBatteryDesignMah.setText(design != null ? design : "—");
        }

        if (tvBatteryCycles != null) {
            int c = readBatteryCycleCount(bm, batteryStatus);
            if (c >= 0) {
                tvBatteryCycles.setText(String.valueOf(c));
            } else {
                tvBatteryCycles.setText("—");
            }
        }
    }

    /**
     * 优先电量广播 {@link BatteryManager#EXTRA_CYCLE_COUNT}，再 {@code getIntProperty}，最后 sysfs。
     */
    private static int readBatteryCycleCount(BatteryManager bm, Intent batteryStatus) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (batteryStatus != null) {
                int fromIntent = batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
                if (fromIntent >= 0) {
                    return fromIntent;
                }
            }
            if (bm != null) {
                int v = bm.getIntProperty(BATTERY_PROPERTY_CYCLE_COUNT_NUM);
                if (v != Integer.MIN_VALUE && v >= 0) {
                    return v;
                }
            }
        }
        return BatterySysFiles.readCycleCount();
    }

    /**
     * 电流：{@code CURRENT_NOW} → {@code CURRENT_AVERAGE} → sysfs；单位按微安换算为毫安（异常大值按纳安缩一次）。
     */
    private static String formatBatteryCurrentMa(BatteryManager bm) {
        long raw = Long.MIN_VALUE;
        if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (now != Integer.MIN_VALUE && now != 0) {
                raw = now;
            } else {
                int avg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (avg != Integer.MIN_VALUE && avg != 0) {
                    raw = avg;
                }
            }
            if (raw == Long.MIN_VALUE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                long nowL = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (nowL != Long.MIN_VALUE && nowL != 0L) {
                    raw = nowL;
                }
            }
        }
        if (raw == Long.MIN_VALUE) {
            long s = BatterySysFiles.readCurrentNowMicroAmps();
            if (s != Long.MIN_VALUE) {
                raw = s;
            }
        }
        if (raw == Long.MIN_VALUE) {
            return null;
        }
        long ma = microAmpsToMilliAmps(raw);
        if (ma == 0L) {
            return "0 mA";
        }
        return String.format(Locale.getDefault(), "%+d mA", ma);
    }

    /**
     * 设计容量：{@code CHARGE_FULL_DESIGN} → {@code CHARGE_FULL} → sysfs {@code charge_full_design} / {@code charge_full}。
     */
    @Nullable
    private static String formatDesignCapacityMah(@Nullable BatteryManager bm) {
        if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int design = bm.getIntProperty(BATTERY_PROPERTY_CHARGE_FULL_DESIGN_NUM);
            if (design != Integer.MIN_VALUE && design > 0) {
                return String.format(Locale.getDefault(), "%d mAh", design / 1000);
            }
            int full = bm.getIntProperty(BATTERY_PROPERTY_CHARGE_FULL_NUM);
            if (full != Integer.MIN_VALUE && full > 0) {
                return String.format(Locale.getDefault(), "%d mAh", full / 1000);
            }
        }
        long d = BatterySysFiles.readChargeFullDesignMicroAh();
        if (d > 0L) {
            return sysfsMicroAhToMahLabel(d);
        }
        long f = BatterySysFiles.readChargeFullMicroAh();
        if (f > 0L) {
            return sysfsMicroAhToMahLabel(f);
        }
        return null;
    }

    private static String sysfsMicroAhToMahLabel(long microAh) {
        if (microAh >= 100_000L) {
            return String.format(Locale.getDefault(), "%d mAh", microAh / 1000L);
        }
        return String.format(Locale.getDefault(), "%d mAh", microAh);
    }

    /** sysfs 多为 µV；若数值较小则按已是 mV 处理。 */
    private static int sysfsMicroVoltsToMilliVolts(long microVolts) {
        long a = Math.abs(microVolts);
        if (a >= 100_000L) {
            return (int) (microVolts / 1000L);
        }
        return (int) microVolts;
    }

    private static long microAmpsToMilliAmps(long rawMicro) {
        long a = Math.abs(rawMicro);
        if (a > 20_000_000L) {
            return rawMicro / 1_000_000L;
        }
        return rawMicro / 1000L;
    }

    /** 充电状态、供电方式、健康（{@link Intent#ACTION_BATTERY_CHANGED}） */
    private static String formatBatteryStatusSummary(Intent intent) {
        if (intent == null) {
            return "—";
        }
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)) {
            return "未检测到电池";
        }
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);

        StringBuilder sb = new StringBuilder(batteryStatusLabel(status));
        boolean chargingLike = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (chargingLike && plug != 0) {
            sb.append(" · ").append(batteryPlugLabel(plug));
        }
        sb.append(" · 健康 ").append(batteryHealthLabel(health));
        return sb.toString();
    }

    private static String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未在充电";
            default:
                return "状态未知";
        }
    }

    private static String batteryPlugLabel(int plug) {
        if (plug == 0) {
            return "未接外电";
        }
        switch (plug) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "AC 电源";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB 供电";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "无线充电";
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && plug == BatteryManager.BATTERY_PLUGGED_DOCK) {
                    return "底座供电";
                }
                return "外接电源";
        }
    }

    private static String batteryHealthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "异常/耗尽";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "过压";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "故障";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "过冷";
            default:
                return "未知";
        }
    }

    private void inflateCpuCoreRingsIfNeeded() {
        if (llCpuCoreRings == null) {
            return;
        }
        llCpuCoreRings.removeAllViews();
        cpuCoreRingHolders.clear();
        int n = CpuSysFiles.getSysfsCpuCount();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (3 * density);
        int rowGap = (int) (8 * density);
        for (int i = 0; i < n; i++) {
            View item = inflater.inflate(R.layout.item_cpu_core_ring, llCpuCoreRings, false);
            TextView tvLabel = item.findViewById(R.id.tvCpuCoreLabel);
            CpuRingGaugeView ring = item.findViewById(R.id.cpuRingGauge);
            TextView tvPct = item.findViewById(R.id.tvCpuCorePct);
            TextView tvFreq = item.findViewById(R.id.tvCpuCoreFreq);
            TextView tvFreqUnit = item.findViewById(R.id.tvCpuCoreFreqUnit);
            TextView tvFreqRange = item.findViewById(R.id.tvCpuCoreFreqRange);
            if (tvLabel != null) {
                tvLabel.setText("核心 " + i);
            }
            // 注意：构造函数为 (rowSpec, columnSpec)，勿与行列下标写反
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 4),
                    GridLayout.spec(i % 4, 1f));
            glp.width = 0;
            glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.setMargins(gap, i >= 4 ? rowGap : 0, gap, gap);
            item.setLayoutParams(glp);
            llCpuCoreRings.addView(item);
            cpuCoreRingHolders.add(new CpuCoreRingHolder(ring, tvPct, tvFreq, tvFreqUnit, tvFreqRange));
        }
    }

    /**
     * 各核：圆环与环心百分比 = 当前频率 / 该核最大频率（cpufreq）；档位色随该比例变化。
     */
    private void refreshCpuCoreRingsUi() {
        if (llCpuCoreRings == null || cpuCoreRingHolders.isEmpty()) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        int colorVariant = ContextCompat.getColor(ctx, R.color.fluent_on_surface_variant);
        int colorOnSurface = ContextCompat.getColor(ctx, R.color.fluent_on_surface);
        Locale loc = Locale.getDefault();
        for (int i = 0; i < cpuCoreRingHolders.size(); i++) {
            CpuCoreRingHolder h = cpuCoreRingHolders.get(i);
            boolean online = CpuSysFiles.isCpuOnline(i);
            long maxK = online ? CpuSysFiles.readCpuMaxFreqKhz(i) : -1L;
            long curK = online ? CpuSysFiles.readCpuCurFreqKhz(i) : -1L;

            int pct = 0;
            boolean haveRatio = online && maxK > 0L && curK > 0L;
            if (haveRatio) {
                pct = Math.round(curK * 100f / (float) maxK);
                if (pct > 100) {
                    pct = 100;
                }
            }
            if (h.ring != null) {
                h.ring.setProgress(haveRatio ? (float) pct : 0f);
            }
            if (h.tvPct != null) {
                if (!online) {
                    h.tvPct.setText("—");
                    h.tvPct.setTextColor(colorVariant);
                } else if (!haveRatio) {
                    h.tvPct.setText("—");
                    h.tvPct.setTextColor(colorOnSurface);
                } else {
                    h.tvPct.setText(pct + "%");
                    h.tvPct.setTextColor(ContextCompat.getColor(ctx, CpuRingGaugeView.stitchTextColorResForProgress((float) pct)));
                }
            }
            if (h.tvFreq != null) {
                if (!online) {
                    h.tvFreq.setText("—");
                    h.tvFreq.setTextColor(colorVariant);
                    if (h.tvFreqUnit != null) {
                        h.tvFreqUnit.setVisibility(View.GONE);
                    }
                    if (h.tvFreqRange != null) {
                        h.tvFreqRange.setText("—");
                        h.tvFreqRange.setTextColor(colorVariant);
                    }
                } else {
                    if (h.tvFreqUnit != null) {
                        h.tvFreqUnit.setVisibility(View.VISIBLE);
                    }
                    long khz = curK;
                    if (khz <= 0) {
                        h.tvFreq.setText("—");
                        h.tvFreq.setTextColor(colorOnSurface);
                    } else {
                        double mhz = khz / 1000.0;
                        String s = mhz >= 1000d
                                ? String.format(loc, "%.0f", mhz)
                                : String.format(loc, "%.1f", mhz);
                        h.tvFreq.setText(s);
                        h.tvFreq.setTextColor(colorOnSurface);
                    }
                    if (h.tvFreqRange != null) {
                        h.tvFreqRange.setText(CpuSysFiles.formatCpuFreqRangeMhzShort(i));
                        h.tvFreqRange.setTextColor(colorVariant);
                    }
                }
            }
        }
    }

    private static final class CpuCoreRingHolder {
        final CpuRingGaugeView ring;
        final TextView tvPct;
        final TextView tvFreq;
        final TextView tvFreqUnit;
        final TextView tvFreqRange;

        CpuCoreRingHolder(CpuRingGaugeView ring, TextView tvPct, TextView tvFreq, TextView tvFreqUnit,
                           TextView tvFreqRange) {
            this.ring = ring;
            this.tvPct = tvPct;
            this.tvFreq = tvFreq;
            this.tvFreqUnit = tvFreqUnit;
            this.tvFreqRange = tvFreqRange;
        }
    }

    private void refreshStorageUi() {
        if (tvStorageUsedTotal == null && pbStorage == null) {
            return;
        }
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
            long avail = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long used = Math.max(0L, total - avail);
            if (tvStorageUsedTotal != null && total > 0) {
                tvStorageUsedTotal.setText(formatGb(used) + " / " + formatGb(total));
            }
            if (pbStorage != null && total > 0) {
                int p = (int) (used * 100L / total);
                pbStorage.setProgress(Math.min(100, Math.max(0, p)));
            }
        } catch (Exception e) {
            if (tvStorageUsedTotal != null) {
                tvStorageUsedTotal.setText("—");
            }
        }
    }

    private void bindGpuSectionAsync() {
        ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (tvGpuApi != null && am != null) {
            ConfigurationInfo info = am.getDeviceConfigurationInfo();
            if (info != null) {
                tvGpuApi.setText(glEsVersionToString(info.reqGlEsVersion));
            }
        }
        if (gpuExecutor == null || tvGpuRenderer == null) {
            return;
        }
        gpuExecutor.execute(() -> {
            String renderer = GpuInfoQuery.queryGlRenderer();
            final String text = TextUtils.isEmpty(renderer) ? Build.HARDWARE : renderer;
            mainHandler.post(() -> {
                if (tvGpuRenderer != null && isAdded()) {
                    tvGpuRenderer.setText(text);
                }
            });
        });
    }

    private void applySelinuxSummary() {
        if (tvSelinuxStatus == null || getContext() == null) {
            return;
        }
        tvSelinuxStatus.setText("读取中…");
        tvSelinuxStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.fluent_on_surface_variant));
        ExecutorService ex = gpuExecutor;
        if (ex == null) {
            return;
        }
        ex.execute(() -> {
            String raw = SelinuxStatusReader.getStatusString();
            String label = formatSelinuxShort(raw);
            mainHandler.post(() -> {
                if (!isAdded() || tvSelinuxStatus == null || getContext() == null) {
                    return;
                }
                tvSelinuxStatus.setText(label);
                int colorRes;
                if (isPermissive(label)) {
                    colorRes = R.color.fluent_orange;
                } else if ("严格模式".equals(label)) {
                    colorRes = R.color.fluent_green_600;
                } else {
                    colorRes = R.color.fluent_on_surface_variant;
                }
                tvSelinuxStatus.setTextColor(ContextCompat.getColor(getContext(), colorRes));
            });
        });
    }

    private boolean isPermissive(String shortStatus) {
        if (shortStatus == null) {
            return false;
        }
        return shortStatus.toLowerCase(Locale.US).contains("permissive")
                || shortStatus.contains("宽容");
    }

    /** 与玩机「SELinux 管理」一致：严格模式 / 宽容模式 / 关闭状态 / 未知 */
    private String formatSelinuxShort(String raw) {
        if (raw == null) {
            return "未知";
        }
        String r = raw.toLowerCase(Locale.US);
        if (r.contains("严格") || r.contains("强制") || r.contains("enforcing")) {
            return "严格模式";
        }
        if (r.contains("宽容") || r.contains("permissive")) {
            return "宽容模式";
        }
        if (r.contains("关闭") || r.contains("disabled")) {
            return "关闭状态";
        }
        if (r.contains("未知")) {
            return "未知";
        }
        return raw;
    }

    private String formatSlotShort(String raw) {
        if (raw == null) {
            return "—";
        }
        if (raw.contains("A")) {
            return "Slot A";
        }
        if (raw.contains("B")) {
            return "Slot B";
        }
        return raw;
    }

    private String getBootSlotRaw() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            String slotSuffix = (String) get.invoke(null, "ro.boot.slot_suffix", "");
            if (slotSuffix != null && !slotSuffix.isEmpty()) {
                if (slotSuffix.equalsIgnoreCase("_a") || slotSuffix.equalsIgnoreCase("a")) {
                    return "A 槽位";
                }
                if (slotSuffix.equalsIgnoreCase("_b") || slotSuffix.equalsIgnoreCase("b")) {
                    return "B 槽位";
                }
                return slotSuffix;
            }
            String slot = (String) get.invoke(null, "ro.boot.slot", "");
            if (slot != null && !slot.isEmpty()) {
                if (slot.equalsIgnoreCase("a") || slot.equalsIgnoreCase("_a")) {
                    return "A 槽位";
                }
                if (slot.equalsIgnoreCase("b") || slot.equalsIgnoreCase("_b")) {
                    return "B 槽位";
                }
                return slot;
            }
            return "未启用 A/B 分区";
        } catch (Exception e) {
            return "未知";
        }
    }

    private String readImeiOrPlaceholder() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return "—";
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                return "未授予 READ_PHONE_STATE";
            }
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return "—";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String imei = tm.getImei();
                if (!TextUtils.isEmpty(imei)) {
                    return imei;
                }
            } else {
                @SuppressWarnings("deprecation")
                String imei = tm.getDeviceId();
                if (!TextUtils.isEmpty(imei)) {
                    return imei;
                }
            }
        } catch (Throwable ignored) {
            return "读取异常";
        }
        return "受限或无 SIM";
    }

    private String readImsiOrPlaceholder() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return "—";
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                return "未授予 READ_PHONE_STATE";
            }
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return "—";
            }
            String imsi = tm.getSubscriberId();
            if (!TextUtils.isEmpty(imsi)) {
                return imsi;
            }
        } catch (Throwable ignored) {
            return "读取异常";
        }
        return "受限或未插卡";
    }

    private String formatGb(long bytes) {
        double gb = bytes / (1024d * 1024d * 1024d);
        if (gb >= 100) {
            return String.format(Locale.getDefault(), "%.0fGB", gb);
        }
        if (gb >= 10) {
            return String.format(Locale.getDefault(), "%.1fGB", gb);
        }
        return String.format(Locale.getDefault(), "%.2fGB", gb);
    }

    private static String glEsVersionToString(int reqGlEsVersion) {
        int major = (reqGlEsVersion & 0xffff0000) >> 16;
        int minor = reqGlEsVersion & 0xffff;
        return "OpenGL ES " + major + "." + minor;
    }
}
