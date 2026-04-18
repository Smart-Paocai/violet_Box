package com.violet.safe;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.scottyab.rootbeer.RootBeer;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "violet_detection_logs";
    private static final String KEY_LOG_PATH = "log_path";

    private int environmentRiskCount = 0;
    private int totalDetectionRiskCount = 0;
    private int riskAppsDetectionPoint = 0;

    private int colorSemanticSafe() {
        return ContextCompat.getColor(this, R.color.ios_semantic_positive);
    }

    private int colorSemanticDanger() {
        return ContextCompat.getColor(this, R.color.ios_semantic_negative);
    }

    private int currentTab = 0;
    private LinearLayout navHome, navDevice, navExplore, navSettings;
    private LinearLayout navRow;
    private ImageView iconHome, iconDevice, iconExplore, iconSettings;
    private TextView textHome, textDevice, textExplore, textSettings;
    private View navLiquidIndicator;
    private AppBarLayout appBarLayout;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        appBarLayout = findViewById(R.id.appBarLayout);
        setSupportActionBar(toolbar);
        toolbar.inflateMenu(R.menu.toolbar_menu);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("紫罗兰Box");
        }
        ensureToolbarVisible();

        // 初始化导航栏
        navHome = findViewById(R.id.nav_home);
        navDevice = findViewById(R.id.nav_device);
        navExplore = findViewById(R.id.nav_explore);
        navSettings = findViewById(R.id.nav_settings);
        navRow = findViewById(R.id.nav_row);
        navLiquidIndicator = findViewById(R.id.nav_liquid_indicator);
        
        iconHome = findViewById(R.id.nav_home_icon);
        iconDevice = findViewById(R.id.nav_device_icon);
        iconExplore = findViewById(R.id.nav_explore_icon);
        iconSettings = findViewById(R.id.nav_settings_icon);
        
        textHome = findViewById(R.id.nav_home_text);
        textDevice = findViewById(R.id.nav_device_text);
        textExplore = findViewById(R.id.nav_explore_text);
        textSettings = findViewById(R.id.nav_settings_text);

        View btnSelinuxManager = findViewById(R.id.btnSelinuxManager);
        if (btnSelinuxManager != null) {
            btnSelinuxManager.setOnClickListener(v ->
                    startActivity(new Intent(this, SelinuxManagerActivity.class)));
        }
        View btnPartitionManager = findViewById(R.id.btnPartitionManager);
        if (btnPartitionManager != null) {
            btnPartitionManager.setOnClickListener(v ->
                    startActivity(new Intent(this, PartitionManagerActivity.class)));
        }
        View btnFontLibraryBackup = findViewById(R.id.btnFontLibraryBackup);
        if (btnFontLibraryBackup != null) {
            btnFontLibraryBackup.setOnClickListener(v ->
                    startActivity(new Intent(this, FontLibraryBackupActivity.class)));
        }
        View btnModuleManager = findViewById(R.id.btnModuleManager);
        if (btnModuleManager != null) {
            btnModuleManager.setOnClickListener(v ->
                    startActivity(new Intent(this, ModuleManagerActivity.class)));
        }

        if (navHome != null) {
            navHome.setOnClickListener(v -> selectTab(0, true));
            navDevice.setOnClickListener(v -> selectTab(1, true));
            navExplore.setOnClickListener(v -> selectTab(2, true));
            navSettings.setOnClickListener(v -> selectTab(3, true));
            selectTab(0, false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        clearLog();
        setupDeviceInfo();
    }

    private void selectTab(int tab) {
        selectTab(tab, true);
    }

    private void selectTab(int tab, boolean animate) {
        if (iconHome == null) return; // Views not initialized
        currentTab = tab;
        ensureToolbarVisible();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("紫罗兰Box");
        }
        resetAllTabs();
        int selectedColor = ContextCompat.getColor(this, R.color.purple_500);
        int defaultColor = ContextCompat.getColor(this, R.color.ios_text_primary);
        LinearLayout selectedNav = null;
        ImageView selectedIcon = null;
        TextView selectedText = null;

        switch (tab) {
            case 0:
                selectedNav = navHome;
                selectedIcon = iconHome;
                selectedText = textHome;
                findViewById(R.id.contentHome).setVisibility(View.VISIBLE);
                findViewById(R.id.contentDevice).setVisibility(View.GONE);
                findViewById(R.id.fragmentExplorePlaceholder).setVisibility(View.GONE);
                findViewById(R.id.fragmentSettingsPlaceholder).setVisibility(View.GONE);
                break;
            case 1:
                selectedNav = navDevice;
                selectedIcon = iconDevice;
                selectedText = textDevice;
                findViewById(R.id.contentHome).setVisibility(View.GONE);
                findViewById(R.id.contentDevice).setVisibility(View.VISIBLE);
                findViewById(R.id.fragmentExplorePlaceholder).setVisibility(View.GONE);
                findViewById(R.id.fragmentSettingsPlaceholder).setVisibility(View.GONE);
                break;
            case 2:
                selectedNav = navExplore;
                selectedIcon = iconExplore;
                selectedText = textExplore;
                findViewById(R.id.contentHome).setVisibility(View.GONE);
                findViewById(R.id.contentDevice).setVisibility(View.GONE);
                findViewById(R.id.fragmentExplorePlaceholder).setVisibility(View.VISIBLE);
                findViewById(R.id.fragmentSettingsPlaceholder).setVisibility(View.GONE);
                break;
            case 3:
                selectedNav = navSettings;
                selectedIcon = iconSettings;
                selectedText = textSettings;
                findViewById(R.id.contentHome).setVisibility(View.GONE);
                findViewById(R.id.contentDevice).setVisibility(View.GONE);
                findViewById(R.id.fragmentExplorePlaceholder).setVisibility(View.GONE);
                findViewById(R.id.fragmentSettingsPlaceholder).setVisibility(View.VISIBLE);
                break;
        }

        if (selectedIcon != null && selectedText != null) {
            if (animate) {
                animateColorTransition(selectedIcon, selectedText, defaultColor, selectedColor);
            } else {
                selectedIcon.setColorFilter(selectedColor);
                selectedText.setTextColor(selectedColor);
            }
        }

        moveLiquidIndicator(selectedNav, animate);

        if (animate) {
            animateNavScale(selectedNav);
        }
    }

    private void ensureToolbarVisible() {
        if (appBarLayout != null) {
            appBarLayout.setVisibility(View.VISIBLE);
            appBarLayout.bringToFront();
        }
    }

    private void resetAllTabs() {
        int defaultColor = ContextCompat.getColor(this, R.color.ios_text_primary);
        
        iconHome.setColorFilter(defaultColor);
        iconDevice.setColorFilter(defaultColor);
        iconExplore.setColorFilter(defaultColor);
        iconSettings.setColorFilter(defaultColor);
        
        textHome.setTextColor(defaultColor);
        textDevice.setTextColor(defaultColor);
        textExplore.setTextColor(defaultColor);
        textSettings.setTextColor(defaultColor);

        navHome.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        navDevice.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        navExplore.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        navSettings.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    private void animateColorTransition(ImageView icon, TextView text, int fromColor, int toColor) {
        ValueAnimator animator = ValueAnimator.ofObject(argbEvaluator, fromColor, toColor);
        animator.setDuration(220);
        animator.addUpdateListener(animation -> {
            int animatedColor = (int) animation.getAnimatedValue();
            icon.setColorFilter(animatedColor);
            text.setTextColor(animatedColor);
        });
        animator.start();
    }

    private void animateNavScale(LinearLayout targetNav) {
        if (targetNav == null) return;
        targetNav.setScaleX(0.94f);
        targetNav.setScaleY(0.94f);
        targetNav.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(140)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .withEndAction(() -> targetNav.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void moveLiquidIndicator(LinearLayout targetNav, boolean animate) {
        if (targetNav == null || navLiquidIndicator == null || navRow == null) return;
        targetNav.post(() -> {
            int targetWidth = targetNav.getWidth();
            if (targetWidth <= 0) return;

            float targetX = targetNav.getX();
            android.view.ViewGroup.LayoutParams params = navLiquidIndicator.getLayoutParams();
            if (params.width != targetWidth) {
                params.width = targetWidth;
                navLiquidIndicator.setLayoutParams(params);
            }

            if (!animate) {
                navLiquidIndicator.setTranslationX(targetX);
                navLiquidIndicator.setScaleX(1f);
                return;
            }

            float currentX = navLiquidIndicator.getTranslationX();
            float distance = Math.abs(targetX - currentX);
            float stretchScale = Math.min(1.28f, 1f + distance / 300f);
            boolean edgeCollision = targetNav == navHome || targetNav == navSettings;
            float direction = targetX >= currentX ? 1f : -1f;
            float overshootPx = edgeCollision ? Math.min(18f, Math.max(6f, distance * 0.08f)) : 0f;
            float collideX = edgeCollision ? (targetX + direction * overshootPx) : targetX;

            ObjectAnimator stretch = ObjectAnimator.ofFloat(navLiquidIndicator, View.SCALE_X, 1f, stretchScale, 1f);
            stretch.setDuration(360);
            stretch.setInterpolator(new DecelerateInterpolator());
            float squashScale = Math.max(0.82f, 1f - (stretchScale - 1f) * 0.65f);
            ObjectAnimator squash = ObjectAnimator.ofFloat(navLiquidIndicator, View.SCALE_Y, 1f, squashScale, 1.06f, 1f);
            squash.setDuration(420);
            squash.setInterpolator(new OvershootInterpolator(1.05f));

            ObjectAnimator slide = ObjectAnimator.ofFloat(navLiquidIndicator, View.TRANSLATION_X, currentX, collideX, targetX);
            slide.setDuration(420);
            slide.setInterpolator(new DecelerateInterpolator());
            ObjectAnimator collideSquash = ObjectAnimator.ofFloat(navLiquidIndicator, View.SCALE_X, 1f, 1f);
            if (edgeCollision) {
                collideSquash = ObjectAnimator.ofFloat(
                        navLiquidIndicator,
                        View.SCALE_X,
                        1f,
                        Math.max(0.92f, 1f - overshootPx / 120f),
                        1f
                );
                collideSquash.setDuration(220);
                collideSquash.setStartDelay(210);
                collideSquash.setInterpolator(new OvershootInterpolator(1.2f));
            }

            AnimatorSet liquidSet = new AnimatorSet();
            liquidSet.playTogether(stretch, squash, slide, collideSquash);
            liquidSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    navLiquidIndicator.animate()
                            .scaleX(0.97f)
                            .scaleY(1.03f)
                            .setDuration(90)
                            .withEndAction(() -> navLiquidIndicator.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(110)
                                    .setInterpolator(new OvershootInterpolator(1.1f))
                                    .start())
                            .start();
                }
            });
            liquidSet.start();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_view_logs) {
            showLogsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private File getLogFile() {
        File dir = new File(getFilesDir(), "logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "detection.log");
    }

    private void appendLog(String text) {
        try {
            File logFile = getLogFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                writer.write("[" + ts + "] " + text);
                writer.newLine();
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_LOG_PATH, logFile.getAbsolutePath())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void clearLog() {
        try {
            File logFile = getLogFile();
            if (logFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private String readRawLogFile() {
        File logFile = getLogFile();
        if (!logFile.exists()) return "暂无日志";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            return "读取日志失败: " + e.getMessage();
        }
        return sb.toString().trim();
    }

    private String readSelinuxRawLogFile() {
        String raw = readRawLogFile();
        if (raw.equals("暂无日志") || raw.startsWith("读取日志失败")) return raw;
        return raw;
    }

    private String readLogFile() {
        return readSelinuxRawLogFile().replace("\n", "\n");
    }

    private void copyLogsToClipboard(String logs) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("detection_logs", logs));
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLogFileLocation() {
        File logFile = getLogFile();
        Uri uri = Uri.fromFile(logFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "打开日志文件"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开日志文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogsDialog() {
        String logs = readSelinuxRawLogFile();
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setView(R.layout.dialog_logs)
                .setPositiveButton("关闭", null)
                .create();
        dialog.show();

        TextView tvLogPath = dialog.findViewById(R.id.tvLogPath);
        TextView tvLogContent = dialog.findViewById(R.id.tvLogContent);
        android.widget.Button positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setText("关闭");
        }
        if (tvLogPath != null) {
            tvLogPath.setText("日志文件: " + getLogFile().getAbsolutePath() + "  ·  点击打开");
            tvLogPath.setOnClickListener(v -> openLogFileLocation());
        }
        if (tvLogContent != null) {
            tvLogContent.setText(logs);
            tvLogContent.setTextIsSelectable(true);
        }

        View copy = dialog.findViewById(R.id.btnCopyLog);
        if (copy != null) copy.setOnClickListener(v -> copyLogsToClipboard(logs));
        View open = dialog.findViewById(R.id.btnOpenLog);
        if (open != null) open.setOnClickListener(v -> openLogFileLocation());
    }

    private void setupDeviceInfo() {
        appendLog("开始设备信息与 SELinux 诊断");
        TextView tvDeviceName = findViewById(R.id.tvDeviceName);
        TextView tvDeviceCode = findViewById(R.id.tvDeviceCode);
        TextView tvAndroidVersion = findViewById(R.id.tvAndroidVersion);
        TextView tvKernelVersion = findViewById(R.id.tvKernelVersion);
        TextView tvSelinuxStatus = findViewById(R.id.tvSelinuxStatus);
        TextView tvBootSlot = findViewById(R.id.tvBootSlot);

        // 设备名称 (通常是厂商+型号)
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        tvDeviceName.setText("设备名称: " + deviceName);

        // 设备代号 (Device code name)
        String deviceCode = Build.DEVICE;
        tvDeviceCode.setText("设备代号: " + deviceCode);

        // 安卓版本 (Release version + API level)
        String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        tvAndroidVersion.setText("安卓版本: Android " + androidVersion);

        // 内核版本
        String kernelVersion = System.getProperty("os.version");
        if (kernelVersion == null) kernelVersion = "未知";
        tvKernelVersion.setText("内核版本: " + kernelVersion);

        // SELinux 状态
        String selinuxStatus = getSELinuxStatus();
        tvSelinuxStatus.setText("SELinux状态: " + selinuxStatus);
        appendLog(buildSelinuxDiagnostics(selinuxStatus));

        // 启动槽位 (A/B)
        String bootSlot = getBootSlot();
        tvBootSlot.setText("启动槽位: " + bootSlot);
        appendLog("启动槽位 = " + bootSlot);

        setupRootDetection();
    }

    private void setupRootDetection() {
        totalDetectionRiskCount = 0;
        environmentRiskCount = 0;
        riskAppsDetectionPoint = 0;

        clearLog();
        checkRiskApps();
        appendLog("开始执行 Root 与安全检测");
        
        android.widget.LinearLayout layoutRootDetails = findViewById(R.id.layoutRootDetails);
        layoutRootDetails.removeAllViews();
        android.widget.LinearLayout layoutAdvancedDetails = findViewById(R.id.layoutAdvancedDetails);
        layoutAdvancedDetails.removeAllViews();
        TextView tvBootloader = findViewById(R.id.tvBootloader);
        TextView tvUsbDebug = findViewById(R.id.tvUsbDebug);

        RootBeer rootBeer = new RootBeer(this);

        // 将每项单独检测提取出来
        addRootCheckItem(layoutRootDetails, "1. 测试签名 (TestKeys)", rootBeer.detectTestKeys());
        addRootCheckItem(layoutRootDetails, "2. Root 管理应用 (SuperSU/Magisk)", rootBeer.detectRootManagementApps());
        addRootCheckItem(layoutRootDetails, "3. Root 隐藏应用 (RootCloakers)", rootBeer.detectRootCloakingApps());
        addRootCheckItem(layoutRootDetails, "4. 潜在危险应用", rootBeer.detectPotentiallyDangerousApps());
        addRootCheckItem(layoutRootDetails, "5. SU 二进制文件存在性", rootBeer.checkForSuBinary());
        addRootCheckItem(layoutRootDetails, "6. SU 命令可用性 (which su)", rootBeer.checkSuExists());
        addRootCheckItem(layoutRootDetails, "7. Magisk 二进制文件", rootBeer.checkForMagiskBinary());
        addRootCheckItem(layoutRootDetails, "8. BusyBox 进程", rootBeer.checkForBusyBoxBinary());
        addRootCheckItem(layoutRootDetails, "9. 系统分区可写 (RWPaths)", rootBeer.checkForRWPaths());
        addRootCheckItem(layoutRootDetails, "10. 危险系统属性", rootBeer.checkForDangerousProps());
        addRootCheckItem(layoutRootDetails, "11. Native层 Root 检测", rootBeer.checkForRootNative());

        com.violet.safe.detector.RootDetector advancedDetector = new com.violet.safe.detector.RootDetector(this);
        addAdvancedRootCheckItem(layoutRootDetails, "12. SU 痕迹检测", advancedDetector.checkSuBinaries());
        addAdvancedRootCheckItem(layoutRootDetails, "14. Root 权限包检测", advancedDetector.checkRootPackages());
        addAdvancedRootCheckItem(layoutRootDetails, "15. Magisk 核心路径", advancedDetector.checkMagiskPaths());
        addAdvancedRootCheckItem(layoutRootDetails, "16. 危险二进制文件", advancedDetector.checkDangerousBinaries());
        addAdvancedRootCheckItem(layoutRootDetails, "17. 隐藏与绕过模块", advancedDetector.checkHideBypassModules());
        addAdvancedRootCheckItem(layoutRootDetails, "18. 危险挂载点扫描", advancedDetector.checkMountPoints());
        addAdvancedRootCheckItem(layoutRootDetails, "19. 危险系统属性", advancedDetector.checkDangerousProperties());
        addAdvancedRootCheckItem(layoutRootDetails, "20. 运行时深度扫描", advancedDetector.checkRuntimeArtifacts());
        addAdvancedRootCheckItem(layoutRootDetails, "21. Data分区 tmpfs", advancedDetector.checkTmpfsOnData());
        addAdvancedRootCheckItem(layoutRootDetails, "22. Root文件时间戳", advancedDetector.checkSuTimestamps());
        addAdvancedRootCheckItem(layoutRootDetails, "23. 非官方ROM(LineageOS)", advancedDetector.checkLineageOS());
        addAdvancedRootCheckItem(layoutRootDetails, "24. 第三方ROM探测", advancedDetector.checkCustomRom());
        addAdvancedRootCheckItem(layoutRootDetails, "25. 构建指纹校验", advancedDetector.checkBuildFieldCoherence());
        addAdvancedRootCheckItem(layoutRootDetails, "26. Zygote 命名空间检测", advancedDetector.checkAdvancedRuntime());
        addAdvancedRootCheckItem(layoutRootDetails, "27. OverlayFS 挂载探测", advancedDetector.checkOverlayFS());
        addAdvancedRootCheckItem(layoutRootDetails, "28. 非官方渠道安装", advancedDetector.checkApkInstallSource());

        checkAdvancedEnvironment(layoutAdvancedDetails);
        checkNativeMechanismsInJava(layoutAdvancedDetails);

        totalDetectionRiskCount = countRiskChildren(layoutRootDetails) + countRiskChildren(layoutAdvancedDetails);

        appendLog("总异常项 = " + totalDetectionRiskCount);

        // 40. Bootloader 锁检测
        String bootloaderStatus = getBootloaderStatus();
        tvBootloader.setText("40. Bootloader锁: " + bootloaderStatus);
        boolean hasBootloaderRisk = false;
        if (bootloaderStatus.contains("已解锁")) {
            tvBootloader.setVisibility(View.VISIBLE);
            styleInlineRiskItem(tvBootloader);
            hasBootloaderRisk = true;
        } else if (bootloaderStatus.contains("已上锁")) {
            tvBootloader.setVisibility(View.GONE);
        } else {
            tvBootloader.setVisibility(View.GONE);
        }
        appendLog("Bootloader = " + bootloaderStatus);

        // 41. USB 调试检测
        boolean isUsbDebugEnabled = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        if (isUsbDebugEnabled) {
            tvUsbDebug.setVisibility(View.VISIBLE);
            tvUsbDebug.setText("41. USB调试: 可疑（已开启）");
            styleInlineWarningItem(tvUsbDebug);
            appendLog("USB调试 = 可疑（已开启）");
        } else {
            tvUsbDebug.setVisibility(View.GONE);
            appendLog("USB调试 = 正常（未开启）");
        }

        // 总览统计口径：明细风险点 + 风险应用模块 + Bootloader 风险（不计 USB 调试）
        environmentRiskCount = totalDetectionRiskCount + riskAppsDetectionPoint + (hasBootloaderRisk ? 1 : 0);

        updateEnvironmentSummary();
    }

    private void addRootCheckItem(android.widget.LinearLayout parent, String label, boolean isDetected) {
        if (!isDetected) {
            return;
        }
        TextView tv = new TextView(this);
        tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setTextSize(14);
        
        tv.setText(label + ": 发现异常痕迹");
        tv.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
        appendLog("RAW " + label + " -> 异常");
        addRiskItemContainer(parent, tv);
    }

    private void addAdvancedRootCheckItem(android.widget.LinearLayout parent, String label, java.util.List<String> detectedItems) {
        if (detectedItems == null || detectedItems.isEmpty()) {
            appendLog(label + " = 正常");
            return;
        }
        TextView tv = new TextView(this);
        tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setTextSize(14);

        StringBuilder sb = new StringBuilder();
        sb.append(label).append(": 发现 ").append(detectedItems.size()).append(" 处异常");
        for (String item : detectedItems) {
            sb.append("\n   - ").append(item);
            appendLog(label + " => " + item);
        }
        tv.setText(sb.toString());
        tv.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
        appendLog(label + " = 异常(" + detectedItems.size() + ")");
        addRiskItemContainer(parent, tv);
    }

    private void checkAdvancedEnvironment(android.widget.LinearLayout parent) {
        boolean hasZygisk = false;
        boolean hasLSPosed = false;
        boolean hasShamiko = false;
        boolean hasRiru = false;
        boolean hasKsu = false;
        boolean hasApatch = false;
        boolean hasSuspiciousEnv = false;
        boolean hasSuspiciousFiles = false;

        // 检测 /proc/self/maps 中的注入库
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.contains("zygisk")) hasZygisk = true;
                if (lowerLine.contains("lspd") || lowerLine.contains("lsposed")) hasLSPosed = true;
                if (lowerLine.contains("shamiko")) hasShamiko = true;
                if (lowerLine.contains("riru")) hasRiru = true;
                if (lowerLine.contains("ksud")) hasKsu = true;
                if (lowerLine.contains("apatch")) hasApatch = true;
            }
        } catch (Exception e) {
            // ignore
        }

        // 检测 /proc/mounts 和 /proc/self/mounts 中的异常挂载点
        boolean hasSuspiciousMounts = false;
        String[] mountPaths = {"/proc/mounts", "/proc/self/mounts"};
        for (String path : mountPaths) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lowerLine = line.toLowerCase();
                    // 典型特征：magisk, ksu, apatch, core/mirror 在 system 上的异常挂载
                    if (lowerLine.contains("magisk") || lowerLine.contains("core/mirror") || 
                        lowerLine.contains("ksu") || lowerLine.contains("apatch")) {
                        hasSuspiciousMounts = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 检测环境变量
        try {
            String ldPreload = System.getenv("LD_PRELOAD");
            if (ldPreload != null && !ldPreload.isEmpty()) {
                String lowerPreload = ldPreload.toLowerCase();
                if (lowerPreload.contains("riru") || lowerPreload.contains("zygisk") || lowerPreload.contains("lsposed")) {
                    hasSuspiciousEnv = true;
                }
            }
            
            String javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS");
            if (javaToolOptions != null && !javaToolOptions.isEmpty()) {
                String lowerOpts = javaToolOptions.toLowerCase();
                if (lowerOpts.contains("riru") || lowerOpts.contains("zygisk") || lowerOpts.contains("lsposed")) {
                    hasSuspiciousEnv = true;
                }
            }
            // (移除对 PATH 的粗暴检查，防止系统自带包含敏感词)
        } catch (Exception e) {
            // ignore
        }

        // 常见敏感文件与目录探测
        String[] suspiciousPaths = {
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ap",
            "/data/adb/modules",
            "/sbin/.magisk",
            "/init.magisk.rc",
            "/system/etc/init/hw/init.magisk.rc",
            "/dev/ksu",
            "/dev/apatch"
        };
        for (String path : suspiciousPaths) {
            File f = new File(path);
            if (f.exists()) {
                hasSuspiciousFiles = true;
                break;
            }
        }

        // 敏感系统属性检测 (扩展)
        boolean hasDangerousProps = false;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            String secure = (String) get.invoke(null, "ro.secure", "1");
            String debuggable = (String) get.invoke(null, "ro.debuggable", "0");
            String tags = (String) get.invoke(null, "ro.build.tags", "");
            
            if ("0".equals(secure) || "1".equals(debuggable) || tags.contains("test-keys")) {
                hasDangerousProps = true;
            }
        } catch (Exception e) {
            // ignore
        }

        addRootCheckItem(parent, "29. Zygisk 注入痕迹", hasZygisk);
        addRootCheckItem(parent, "30. LSPosed/Xposed 框架", hasLSPosed);
        addRootCheckItem(parent, "31. Riru 注入框架", hasRiru);
        addRootCheckItem(parent, "32. Shamiko 隐藏模块", hasShamiko);
        addRootCheckItem(parent, "33. KernelSU (KSU) 痕迹", hasKsu);
        addRootCheckItem(parent, "34. APatch 痕迹", hasApatch);
        addRootCheckItem(parent, "35. 异常系统挂载 (Magisk/KSU/APatch)", hasSuspiciousMounts);
        addRootCheckItem(parent, "36. 异常环境变量 (LD_PRELOAD等)", hasSuspiciousEnv);
        addRootCheckItem(parent, "37. 敏感系统文件或目录", hasSuspiciousFiles);
        addRootCheckItem(parent, "38. 危险系统属性 (Secure/Debug/Tags)", hasDangerousProps);
    }

    private void checkNativeMechanismsInJava(android.widget.LinearLayout parent) {
        String[] suPaths = {
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/sbin/su", "/su/bin/su", "/system/bin/su", "/system/bin/.ext/su",
            "/system/bin/failsafe/su", "/system/sd/xbin/su", "/system/usr/we-need-root/su",
            "/system/xbin/su", "/cache/su", "/data/su", "/dev/su"
        };

        String[] magiskPaths = {
            "/sbin/.magisk/", "/sbin/.core/mirror", "/sbin/.core/img",
            "/sbin/.core/db-0/magisk.db", "/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap"
        };

        boolean foundSuViaOs = false;
        for (String path : suPaths) {
            try {
                // 使用 Os API 直接发起 syscall 级别的 access 检测
                if (Os.access(path, OsConstants.F_OK)) {
                    foundSuViaOs = true;
                    break;
                }
            } catch (Exception e) {
                // ignore
            }
            
            try {
                // 使用 Os API 发起 syscall 级别的 stat 检测
                StructStat stat = Os.stat(path);
                if (stat != null) {
                    foundSuViaOs = true;
                    break;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        boolean foundMagiskViaOs = false;
        for (String path : magiskPaths) {
            try {
                if (Os.access(path, OsConstants.F_OK)) {
                    foundMagiskViaOs = true;
                    break;
                }
            } catch (Exception e) {
                // ignore
            }

            try {
                StructStat stat = Os.stat(path);
                if (stat != null) {
                    foundMagiskViaOs = true;
                    break;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        addRootCheckItem(parent, "39. [Os API] 底层 SU 文件穿透检测", foundSuViaOs);
        addRootCheckItem(parent, "40. [Os API] 底层敏感目录穿透探测", foundMagiskViaOs);
    }

    private int countRiskChildren(android.widget.LinearLayout parent) {
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && "risk_item".equals(tag)) {
                count++;
            }
        }
        return count;
    }

    private void addRiskItemContainer(android.widget.LinearLayout parent, TextView contentView) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag("risk_item");
        container.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        android.widget.LinearLayout.LayoutParams containerParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = dpToPx(6);
        containerParams.bottomMargin = dpToPx(10);
        container.setLayoutParams(containerParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FF4D4F);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0x33FF4D4F);
        container.setBackground(bg);

        container.addView(contentView);
        parent.addView(container);
    }

    private void styleInlineRiskItem(TextView tv) {
        tv.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
        tv.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = dpToPx(6);
        lp.bottomMargin = dpToPx(10);
        tv.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FF4D4F);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0x33FF4D4F);
        tv.setBackground(bg);
    }

    private void styleInlineWarningItem(TextView tv) {
        tv.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
        tv.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = dpToPx(6);
        lp.bottomMargin = dpToPx(10);
        tv.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FFB74D);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0x33FF9800);
        tv.setBackground(bg);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateEnvironmentSummary() {
        TextView tvEnvironmentSummary = findViewById(R.id.tvEnvironmentSummary);
        TextView tvEnvironmentDetail = findViewById(R.id.tvEnvironmentDetail);
        ImageView ivEnvironmentIcon = findViewById(R.id.ivEnvironmentIcon);
        
        if (tvEnvironmentSummary == null) return;

        if (environmentRiskCount == 0) {
            tvEnvironmentSummary.setText("设备安全");
            tvEnvironmentSummary.setTextColor(colorSemanticSafe());
            if (tvEnvironmentDetail != null) {
                tvEnvironmentDetail.setText("未检测到安全风险");
            }
            if (ivEnvironmentIcon != null) {
                ivEnvironmentIcon.setImageResource(R.drawable.ic_safe);
            }
            appendLog("设备总览 = 正常设备");
        } else {
            tvEnvironmentSummary.setText("检测到风险");
            tvEnvironmentSummary.setTextColor(colorSemanticDanger());
            if (tvEnvironmentDetail != null) {
                tvEnvironmentDetail.setText("发现 " + environmentRiskCount + " 个安全问题");
            }
            if (ivEnvironmentIcon != null) {
                ivEnvironmentIcon.setImageResource(R.drawable.ic_risk);
            }
            appendLog("设备总览 = 风险设备");
        }

        updateSystemPropsPlaceholder(true);
    }

    private void updateSystemPropsPlaceholder(boolean shouldShow) {
        View card = findViewById(R.id.cardSystemPropsPlaceholder);
        TextView tvProps = findViewById(R.id.tvSystemPropsPlaceholder);
        if (card == null || tvProps == null) {
            return;
        }
        if (!shouldShow) {
            card.setVisibility(View.GONE);
            return;
        }

        card.setVisibility(View.VISIBLE);
        tvProps.setText(buildFullSystemPropsText());
    }

    private String buildFullSystemPropsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("品牌: ").append(Build.BRAND).append("\n");
        sb.append("厂商: ").append(Build.MANUFACTURER).append("\n");
        sb.append("型号: ").append(Build.MODEL).append("\n");
        sb.append("设备代号: ").append(Build.DEVICE).append("\n");
        sb.append("产品名称: ").append(Build.PRODUCT).append("\n");
        sb.append("系统版本: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("构建类型: ").append(Build.TYPE).append("\n");
        sb.append("构建标签: ").append(Build.TAGS).append("\n");
        sb.append("构建指纹: ").append(Build.FINGERPRINT).append("\n");
        sb.append("内核: ").append(System.getProperty("os.version", "unknown")).append("\n");
        sb.append("ro.secure: ").append(readSystemProp("ro.secure", "unknown")).append("\n");
        sb.append("ro.debuggable: ").append(readSystemProp("ro.debuggable", "unknown")).append("\n");
        sb.append("ro.boot.flash.locked: ").append(readSystemProp("ro.boot.flash.locked", "unknown")).append("\n");
        sb.append("ro.boot.verifiedbootstate: ").append(readSystemProp("ro.boot.verifiedbootstate", "unknown")).append("\n");
        sb.append("ro.boot.vbmeta.device_state: ").append(readSystemProp("ro.boot.vbmeta.device_state", "unknown")).append("\n");
        sb.append("ro.boot.slot_suffix: ").append(readSystemProp("ro.boot.slot_suffix", "unknown"));
        return sb.toString();
    }

    private String readSystemProp(String key, String fallback) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void checkRiskApps() {
        TextView tvRiskApps = findViewById(R.id.tvRiskApps);
        
        String[] targetPackages = {
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "io.github.vvb2060.magisk.lite",
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager",
            "top.canyie.dreamland.manager",
            "me.weishu.exp",
            "com.tsng.hidemyapplist",
            "cn.geektang.privacyspace",
            "moe.shizuku.redirectstorage",
            "org.lsposed.lspatch",
            "org.lsposed.patch",
            "com.solohsu.android.edxp.manager",
            "io.github.whale.manager",
            "com.github.kr328.clash",
            "com.github.kr328.riru",
            "io.github.riyukichi.hidemyroot",
            "com.github.tyty19728887777.android.systemless",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.luckypatcher",
            "com.github.kyuubiran.ezxhelper",
            "com.sukisu.ultra"
        };

        PackageManager pm = getPackageManager();
        List<String> foundApps = new ArrayList<>();
        
        for (String pkg : targetPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                foundApps.add(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                // 未安装该应用，跳过
            }
        }

        if (foundApps.isEmpty()) {
            riskAppsDetectionPoint = 0;
            tvRiskApps.setVisibility(View.GONE);
            appendLog("风险应用: 未发现");
        } else {
            riskAppsDetectionPoint = 1;
            tvRiskApps.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("发现以下风险应用：\n");
            for (String app : foundApps) {
                sb.append("- ").append(app).append("\n");
            }
            tvRiskApps.setText(sb.toString().trim());
            styleInlineRiskItem(tvRiskApps);
            appendLog("风险应用: 发现 " + foundApps.size() + " 项");
            for (String app : foundApps) {
                appendLog("风险应用项 = " + app);
            }
        }
    }

    /**
     * 读取 /sys/fs/selinux/status 中的 “SELinux status:” 行。
     *
     * @return null 表示无法解析；true 表示已启用；false 表示完全未启用（关闭）
     */
    private Boolean readSelinuxEnabledFromStatusNode() {
        File statusFile = new File("/sys/fs/selinux/status");
        if (!statusFile.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (!t.startsWith("SELinux status:")) {
                    continue;
                }
                String rest = t.substring(t.indexOf(':') + 1).trim().toLowerCase();
                if (rest.contains("disabled")) {
                    return Boolean.FALSE;
                }
                if (rest.contains("enabled")) {
                    return Boolean.TRUE;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 读取 /sys/fs/selinux/enforce：1=强制，0=宽容（仅在 SELinux 已启用时有定义）。
     */
    private String readEnforceNodeOrNull() {
        File enforceFile = new File("/sys/fs/selinux/enforce");
        if (!enforceFile.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(enforceFile))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readEnforceViaGetenforce() {
        String[] getenforcePaths = {
            "/system/bin/getenforce",
            "/vendor/bin/getenforce",
            "/system_ext/bin/getenforce"
        };
        for (String path : getenforcePaths) {
            if (!new File(path).isFile()) continue;
            try {
                Process p = new ProcessBuilder(path).redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = r.readLine();
                    if (p.waitFor() == 0 && line != null) {
                        String l = line.trim().toLowerCase();
                        if (l.equals("enforcing") || l.equals("permissive") || l.equals("disabled")) {
                            return l;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String readSelinuxApiStatus() {
        try {
            Class<?> selinuxClass = Class.forName("android.os.SELinux");
            Method isSELinuxEnabled = selinuxClass.getMethod("isSELinuxEnabled");
            Method isSELinuxEnforced = selinuxClass.getMethod("isSELinuxEnforced");
            
            boolean apiEnabled = (boolean) isSELinuxEnabled.invoke(null);
            android.util.Log.d("SELinux", "API - Enabled: " + apiEnabled);
            
            if (!apiEnabled) {
                return "disabled";
            }
            
            boolean enforced = (boolean) isSELinuxEnforced.invoke(null);
            android.util.Log.d("SELinux", "API - Enforced: " + enforced);
            
            return enforced ? "enforcing" : "permissive";
        } catch (Exception e) {
            android.util.Log.e("SELinux", "API检测失败", e);
        }
        return null;
    }

    private String getSELinuxStatus() {
        // 方法1：使用反射调用 android.os.SELinux API（最可靠）
        try {
            Class<?> selinuxClass = Class.forName("android.os.SELinux");
            Method isSELinuxEnabled = selinuxClass.getMethod("isSELinuxEnabled");
            Method isSELinuxEnforced = selinuxClass.getMethod("isSELinuxEnforced");
            
            Boolean enabled = (Boolean) isSELinuxEnabled.invoke(null);
            android.util.Log.d("SELinux", "反射API - Enabled: " + enabled);
            
            if (enabled != null && !enabled) {
                return "关闭状态 (SELinux 未启用)";
            }
            
            Boolean enforced = (Boolean) isSELinuxEnforced.invoke(null);
            android.util.Log.d("SELinux", "反射API - Enforced: " + enforced);
            
            if (enforced != null) {
                return enforced ? "强制模式 (Enforcing)" : "宽容模式 (Permissive)";
            }
        } catch (Exception e) {
            android.util.Log.e("SELinux", "反射API调用失败", e);
        }

        // 方法2：使用 getenforce 命令
        String getenforceVal = readEnforceViaGetenforce();
        android.util.Log.d("SELinux", "getenforce命令返回值: " + getenforceVal);
        if (getenforceVal != null) {
            if ("enforcing".equals(getenforceVal)) return "强制模式 (Enforcing)";
            if ("permissive".equals(getenforceVal)) return "宽容模式 (Permissive)";
            if ("disabled".equals(getenforceVal)) return "关闭状态 (SELinux 未启用)";
        }

        // 方法3：读取 /sys/fs/selinux/enforce 文件
        String enforceVal = readEnforceNodeOrNull();
        android.util.Log.d("SELinux", "enforce文件返回值: " + enforceVal);
        if (enforceVal != null) {
            if ("1".equals(enforceVal)) return "强制模式 (Enforcing)";
            if ("0".equals(enforceVal)) return "宽容模式 (Permissive)";
        }

        // 方法4：检查 /sys/fs/selinux/status 文件
        Boolean enabled = readSelinuxEnabledFromStatusNode();
        android.util.Log.d("SELinux", "status文件返回值: " + enabled);
        if (Boolean.FALSE.equals(enabled)) {
            return "关闭状态 (SELinux 未启用)";
        }

        return "未知 (权限不足或系统不支持)";
    }

    private String getBootloaderStatus() {
        // 优先读取 /proc/cmdline (内核启动参数，更底层，较难被纯 Java 层 Hook)
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cmdline"))) {
            String cmdline = reader.readLine();
            if (cmdline != null) {
                if (cmdline.contains("androidboot.flash.locked=0") || 
                    cmdline.contains("androidboot.vbmeta.device_state=unlocked") ||
                    cmdline.contains("androidboot.verifiedbootstate=orange") ||
                    cmdline.contains("androidboot.verifiedbootstate=yellow")) {
                    return "已解锁 (底层 cmdline 检测)";
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 读取 /proc/bootconfig (Android 12+ 引入，用于替代部分 cmdline 功能)
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/bootconfig"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("androidboot.flash.locked = \"0\"") || 
                    line.contains("androidboot.vbmeta.device_state = \"unlocked\"") ||
                    line.contains("androidboot.verifiedbootstate = \"orange\"") ||
                    line.contains("androidboot.verifiedbootstate = \"yellow\"")) {
                    return "已解锁 (底层 bootconfig 检测)";
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // Fallback 到系统属性 SystemProperties (容易被 resetprop 等工具修改)
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            
            // 尝试读取 ro.boot.flash.locked 属性
            String flashLocked = (String) get.invoke(null, "ro.boot.flash.locked", "");
            if ("0".equals(flashLocked)) return "已解锁 (属性: Unlocked)";
            if ("1".equals(flashLocked)) return "已上锁 (属性: Locked)";
            
            // 尝试读取 verifiedbootstate 属性
            String verifiedBootState = (String) get.invoke(null, "ro.boot.verifiedbootstate", "");
            if ("orange".equals(verifiedBootState) || "yellow".equals(verifiedBootState)) {
                return "已解锁 (属性: " + verifiedBootState + " state)";
            }
            if ("green".equals(verifiedBootState)) {
                return "已上锁 (属性: green state)";
            }
            
            // 尝试读取 vbmeta.device_state
            String vbmetaState = (String) get.invoke(null, "ro.boot.vbmeta.device_state", "");
            if ("unlocked".equals(vbmetaState)) return "已解锁 (属性: vbmeta unlocked)";
            if ("locked".equals(vbmetaState)) return "已上锁 (属性: vbmeta locked)";

            return "未知 (无法读取状态)";
        } catch (Exception e) {
            return "未知 (获取异常)";
        }
    }

    private String buildSelinuxDiagnostics(String finalStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELinux诊断开始");
        String enforceVal = readEnforceNodeOrNull();
        String getenforceVal = readEnforceViaGetenforce();
        Boolean enabled = readSelinuxEnabledFromStatusNode();
        String apiVal = readSelinuxApiStatus();
        sb.append(" | enforce=").append(String.valueOf(enforceVal));
        sb.append(" | getenforce=").append(String.valueOf(getenforceVal));
        sb.append(" | status=").append(String.valueOf(enabled));
        sb.append(" | api=").append(String.valueOf(apiVal));
        sb.append(" | final=").append(finalStatus);
        return sb.toString();
    }

    private String getBootSlot() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            
            // 尝试读取 ro.boot.slot_suffix 属性
            String slotSuffix = (String) get.invoke(null, "ro.boot.slot_suffix", "");
            if (slotSuffix != null && !slotSuffix.isEmpty()) {
                if (slotSuffix.equalsIgnoreCase("_a") || slotSuffix.equalsIgnoreCase("a")) return "A 槽位";
                if (slotSuffix.equalsIgnoreCase("_b") || slotSuffix.equalsIgnoreCase("b")) return "B 槽位";
                return slotSuffix;
            }
            
            // 如果上一个为空，尝试读取 ro.boot.slot 属性
            String slot = (String) get.invoke(null, "ro.boot.slot", "");
            if (slot != null && !slot.isEmpty()) {
                if (slot.equalsIgnoreCase("a") || slot.equalsIgnoreCase("_a")) return "A 槽位";
                if (slot.equalsIgnoreCase("b") || slot.equalsIgnoreCase("_b")) return "B 槽位";
                return slot;
            }
            
            return "未启用 A/B 分区或获取失败";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未知";
    }
}