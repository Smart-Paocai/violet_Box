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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.scottyab.rootbeer.RootBeer;
import com.violet.safe.R;
import com.violet.safe.util.CpuStatReader;
import com.violet.safe.util.CpuSysFiles;
import com.violet.safe.util.GpuInfoQuery;
import com.violet.safe.util.SelinuxStatusReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private CpuStatReader cpuPrevSnapshot;

    private final Runnable cpuTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            CpuStatReader cur = CpuStatReader.read();
            if (cur != null && cpuPrevSnapshot != null) {
                int[] pct = CpuStatReader.computePercents(cpuPrevSnapshot, cur);
                if (pct != null) {
                    if (pbCpu1 != null) {
                        pbCpu1.setProgress(pct[0]);
                    }
                    if (tvCpuTotalPct != null) {
                        tvCpuTotalPct.setText(pct[0] + "%");
                    }
                    if (pbCpu2 != null) {
                        pbCpu2.setProgress(pct[1]);
                    }
                    if (tvCpuUserPct != null) {
                        tvCpuUserPct.setText(pct[1] + "%");
                    }
                    if (pbCpu3 != null) {
                        pbCpu3.setProgress(pct[2]);
                    }
                    if (tvCpuIoPct != null) {
                        tvCpuIoPct.setText(pct[2] + "%");
                    }
                }
            }
            cpuPrevSnapshot = cur;

            if (tvCpuSocSummary != null) {
                tvCpuSocSummary.setText(CpuSysFiles.buildCpuSummaryLine());
            }
            if (tvCpuFreqLine != null) {
                tvCpuFreqLine.setText("CPU0 当前频率 · " + CpuSysFiles.readCpu0CurFreqGHz());
            }

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

    private ProgressBar pbCpu1;
    private ProgressBar pbCpu2;
    private ProgressBar pbCpu3;
    private TextView tvCpuTotalPct;
    private TextView tvCpuUserPct;
    private TextView tvCpuIoPct;
    private TextView tvCpuSocSummary;
    private TextView tvCpuFreqLine;

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

        pbCpu1 = view.findViewById(R.id.pbCpuCluster1);
        pbCpu2 = view.findViewById(R.id.pbCpuCluster2);
        pbCpu3 = view.findViewById(R.id.pbCpuCluster3);
        tvCpuTotalPct = view.findViewById(R.id.tvCpuTotalPct);
        tvCpuUserPct = view.findViewById(R.id.tvCpuUserPct);
        tvCpuIoPct = view.findViewById(R.id.tvCpuIoPct);
        tvCpuSocSummary = view.findViewById(R.id.tvCpuSocSummary);
        tvCpuFreqLine = view.findViewById(R.id.tvCpuFreqLine);

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
        TextView tvKernelBuildDate = view.findViewById(R.id.tvKernelBuildDate);

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
        if (tvKernelBuildDate != null) {
            tvKernelBuildDate.setText(formatKernelDisplayLine());
        }

        bindGpuSectionAsync();

        requestPhonePermissionIfNeeded();
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

        cpuPrevSnapshot = CpuStatReader.read();
        mainHandler.removeCallbacks(cpuTickRunnable);
        mainHandler.post(cpuTickRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        mainHandler.removeCallbacks(cpuTickRunnable);
        cpuPrevSnapshot = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (gpuExecutor != null) {
            gpuExecutor.shutdownNow();
            gpuExecutor = null;
        }
        pbCpu1 = null;
        pbCpu2 = null;
        pbCpu3 = null;
        tvCpuTotalPct = null;
        tvCpuUserPct = null;
        tvCpuIoPct = null;
        tvCpuSocSummary = null;
        tvCpuFreqLine = null;
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

    private String readKernelVersionLine() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/version"))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 内核构建一行：优先 /proc/version；若不可用则将 Build.TIME 格式化为可读时间 */
    private String formatKernelDisplayLine() {
        String line = readKernelVersionLine();
        if (line != null && !line.isEmpty()) {
            return line;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(Build.TIME));
        } catch (Exception e) {
            return Build.TIME > 0 ? String.valueOf(Build.TIME) : "—";
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
