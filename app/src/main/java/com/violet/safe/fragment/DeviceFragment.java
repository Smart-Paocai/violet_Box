package com.violet.safe.fragment;

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
import com.violet.safe.util.CpuSysFiles;
import com.violet.safe.util.GpuInfoQuery;
import com.violet.safe.util.SelinuxStatusReader;
import com.violet.safe.widget.CpuRingGaugeView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceFragment extends Fragment {

    private static final int REQ_READ_PHONE = 6011;
    /**
     * {@link BatteryManager#getIntProperty(int)} 属性 id，与 {@link BatteryManager#BATTERY_PROPERTY_CHARGE_FULL}
     * 相同（API 28+）；部分 SDK 存根缺少该字段故使用字面量。
     */
    private static final int BATTERY_PROPERTY_CHARGE_FULL_ID = 6;
    /**
     * 与 {@link BatteryManager#BATTERY_PROPERTY_CYCLE_COUNT} 相同（API 34+）。
     */
    private static final int BATTERY_PROPERTY_CYCLE_COUNT_ID = 7;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 各核圆环与存储占用：每秒刷新 */
    private final Runnable cpuRingTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
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
        TextView tvAndroidId = view.findViewById(R.id.tvAndroidId);
        tvImei = view.findViewById(R.id.tvImei);
        tvImsi = view.findViewById(R.id.tvImsi);

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
        if (tvAndroidId != null) {
            String androidId = Settings.Secure.getString(requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            tvAndroidId.setText(androidId != null ? androidId : "—");
        }
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

    private void applyBatteryIntentInner(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
        if (tvBatteryHealth != null && pct >= 0) {
            tvBatteryHealth.setText(pct + "%");
        }

        if (tvBatteryTemp != null) {
            int t = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", t / 10f));
        }

        if (tvBatteryVoltage != null) {
            int v = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (v > 0) {
                tvBatteryVoltage.setText(v + " mV");
            } else {
                tvBatteryVoltage.setText("—");
            }
        }

        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);

        if (tvBatteryCurrent != null && bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int curr = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (curr != Integer.MIN_VALUE && curr != 0) {
                tvBatteryCurrent.setText(String.format(Locale.getDefault(), "%+d mA", curr / 1000));
            } else {
                tvBatteryCurrent.setText("—");
            }
        }

        if (tvBatteryDesignMah != null && bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int microAh = bm.getIntProperty(BATTERY_PROPERTY_CHARGE_FULL_ID);
            if (microAh != Integer.MIN_VALUE && microAh > 0) {
                tvBatteryDesignMah.setText(String.format(Locale.getDefault(), "%d mAh", microAh / 1000));
            } else {
                tvBatteryDesignMah.setText("—");
            }
        } else if (tvBatteryDesignMah != null) {
            tvBatteryDesignMah.setText("—");
        }

        if (tvBatteryCycles != null && bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int c = bm.getIntProperty(BATTERY_PROPERTY_CYCLE_COUNT_ID);
            if (c != Integer.MIN_VALUE && c >= 0) {
                tvBatteryCycles.setText(String.valueOf(c));
            } else {
                tvBatteryCycles.setText("—");
            }
        } else if (tvBatteryCycles != null) {
            tvBatteryCycles.setText("需 Android 14+ 且设备上报");
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
