package com.violet.box.ui.main;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

import com.violet.box.ui.appmanager.AppManagerActivity;
import com.violet.box.ui.about.AboutActivity;
import com.violet.box.ui.font.FontLibraryBackupActivity;
import com.violet.box.ui.module.ModuleManagerActivity;
import com.violet.box.ui.partition.PartitionManagerActivity;
import com.violet.box.ui.payload.PayloadActivity;
import com.violet.box.ui.plugin.GlobalDeviceSpoofActivity;
import com.violet.box.ui.plugin.DeviceIdModifyActivity;
import com.violet.box.ui.plugin.VioletPluginActivity;
import com.violet.box.ui.safety.SafetyPageController;
import com.violet.box.ui.selinux.SelinuxManagerActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.ui.platform.ComposeView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.violet.box.BuildConfig;
import com.violet.box.R;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_TAB = "extra_open_tab";

    // Prefs 名沿用旧的检测日志存储名，保留用户已有的自动检查更新开关状态
    private static final String PREFS_NAME = "violet_detection_logs";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String UPDATE_JSON_URL = "https://gitee.com/smartpaocai/smart-tool/raw/master/violetbox.json";
    private static final long EXPLORE_ROOT_STATUS_CACHE_MS = 5_000L;

    private int colorSemanticSafe() {
        return ContextCompat.getColor(this, R.color.ios_semantic_positive);
    }

    private int currentTab = 0;
    private LinearLayout navHome, navDevice, navExplore, navSettings;
    private LinearLayout navRow;
    private ImageView iconHome, iconDevice, iconExplore, iconSettings;
    private TextView textHome, textDevice, textExplore, textSettings;
    private View navLiquidIndicator;
    private AppBarLayout appBarLayout;
    private View overflowMenuScrim;
    private PopupMenu quickRebootPopupMenu;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private AnimatorSet tabSwitchAnimator;

    private View nativeContentView;
    private SafetyPageController safetyPageController;
    private volatile boolean exploreAutoInstallTriggered = false;
    private volatile boolean exploreAutoInstallRunning = false;
    private volatile ExploreRootStatus cachedExploreRootStatus;
    private volatile long cachedExploreRootStatusAt;
    private volatile boolean exploreRootStatusRefreshRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable exploreResumeRefresh = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || currentTab != 2) {
                return;
            }
            updateExploreRootStatus(false);
            updateExploreUptime();
        }
    };

    private static final class ExploreRootStatus {
        final boolean rootGranted;
        final String manager;

        ExploreRootStatus(boolean rootGranted, String manager) {
            this.rootGranted = rootGranted;
            this.manager = manager;
        }
    }

    @Override
    public <T extends View> T findViewById(int id) {
        if (nativeContentView != null) {
            T view = nativeContentView.findViewById(id);
            if (view != null) {
                return view;
            }
        }
        return super.findViewById(id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightNavigationBars(false);
            controller.setAppearanceLightStatusBars(true);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.navigationBars());
        }

        nativeContentView = getLayoutInflater().inflate(R.layout.activity_main, null, false);
        ComposeView composeView = new ComposeView(this);
        setContentView(composeView);

        Toolbar toolbar = findViewById(R.id.toolbar);
        appBarLayout = findViewById(R.id.appBarLayout);
        overflowMenuScrim = findViewById(R.id.overflowMenuScrim);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }
        ensureToolbarVisible();
        if (overflowMenuScrim != null) {
            overflowMenuScrim.setOnClickListener(v -> {
                if (quickRebootPopupMenu != null) {
                    quickRebootPopupMenu.dismiss();
                } else {
                    setOverflowMenuScrimVisible(false);
                }
            });
        }

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
        View btnModuleBackup = findViewById(R.id.btnModuleBackup);
        if (btnModuleBackup != null) {
            btnModuleBackup.setOnClickListener(v ->
                    startActivity(new Intent(this, com.violet.box.ui.module.ModuleBackupActivity.class)));
        }
        View btnAppManager = findViewById(R.id.btnAppManager);
        if (btnAppManager != null) {
            btnAppManager.setOnClickListener(v -> openSystemAppManagement());
        }
        View btnPayload = findViewById(R.id.btnPayload);
        if (btnPayload != null) {
            btnPayload.setOnClickListener(v ->
                    startActivity(new Intent(this, PayloadActivity.class)));
        }
        View btnVioletPlugin = findViewById(R.id.btnVioletPlugin);
        if (btnVioletPlugin != null) {
            btnVioletPlugin.setOnClickListener(v ->
                    startActivity(new Intent(this, VioletPluginActivity.class)));
        }
        View btnGlobalDeviceSpoof = findViewById(R.id.btnGlobalDeviceSpoof);
        if (btnGlobalDeviceSpoof != null) {
            btnGlobalDeviceSpoof.setOnClickListener(v ->
                    startActivity(new Intent(this, GlobalDeviceSpoofActivity.class)));
        }
        View btnDeviceIdModify = findViewById(R.id.btnDeviceIdModify);
        if (btnDeviceIdModify != null) {
            btnDeviceIdModify.setOnClickListener(v ->
                    startActivity(new Intent(this, DeviceIdModifyActivity.class)));
        }
        View cardGithubRepo = findViewById(R.id.cardGithubRepo);
        if (cardGithubRepo != null) {
            cardGithubRepo.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Smart-Paocai/violet_Box"));
                try {
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开 GitHub 链接", Toast.LENGTH_SHORT).show();
                }
            });
        }
        View cardTelegramChannel = findViewById(R.id.cardTelegramChannel);
        if (cardTelegramChannel != null) {
            cardTelegramChannel.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/violettoolbox"));
                try {
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开 Telegram 频道链接", Toast.LENGTH_SHORT).show();
                }
            });
        }
        View cardAbout = findViewById(R.id.cardAbout);
        if (cardAbout != null) {
            cardAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        }
        com.violet.box.ui.widget.KsuSwitchView switchCheckUpdate = findViewById(R.id.switchCheckUpdate);
        View cardCheckUpdate = findViewById(R.id.cardCheckUpdate);
        SharedPreferences updatePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean autoCheckUpdate = updatePrefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (switchCheckUpdate != null) {
            switchCheckUpdate.setChecked(autoCheckUpdate);
            switchCheckUpdate.setOnCheckedChange(isChecked ->
                    updatePrefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATE, isChecked).apply());
        }
        if (cardCheckUpdate != null) {
            cardCheckUpdate.setOnClickListener(v -> {
                Toast.makeText(this, "正在手动检测更新...", Toast.LENGTH_SHORT).show();
                checkVersionUpdate(true);
            });
        }
        if (autoCheckUpdate) {
            checkVersionUpdate(false);
        } else {
            hideNewVersionCard();
        }
        int defaultTab = 0;
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_OPEN_TAB)) {
            int requested = intent.getIntExtra(EXTRA_OPEN_TAB, defaultTab);
            if (requested >= 0 && requested <= 3) {
                defaultTab = requested;
            }
        }
        currentTab = defaultTab;
        BottomBarState.INSTANCE.setSelectedTab(defaultTab);
        
        FloatingBottomBarComposeKt.attachMainScreen(composeView, nativeContentView, new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer index) {
                selectTab(index != null ? index : 0, true);
                return Unit.INSTANCE;
            }
        });

        selectTab(defaultTab, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 底部 inset 交由 Compose 悬浮底栏自己处理，避免出现“底部容器包裹感”
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        updateExploreRootStatus(true);
        updateExploreUptime();

        safetyPageController = new SafetyPageController(this);
        safetyPageController.initialize();

        // 预热一次 Expressive 开关组合：首次组合的类加载/JIT 成本在启动期消化，
        // 避免首次进入安全页时集中付出（1×1 隐藏视图，首帧绘制后即移除）
        com.violet.box.ui.widget.KsuSwitchView warmupSwitch = new com.violet.box.ui.widget.KsuSwitchView(this);
        warmupSwitch.setVisibility(View.INVISIBLE);
        ViewGroup warmupRoot = findViewById(R.id.main);
        warmupRoot.addView(warmupSwitch, new ViewGroup.LayoutParams(1, 1));
        warmupSwitch.post(() -> warmupRoot.removeView(warmupSwitch));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (safetyPageController != null) {
            safetyPageController.onMaybeShown();
        }
        mainHandler.removeCallbacks(exploreResumeRefresh);
        // 返回时先让界面完成首帧绘制，再延后刷新 Explore 状态，降低体感卡顿。
        if (currentTab == 2) {
            updateExploreUptime();
            View exploreView = findViewById(R.id.fragmentExplorePlaceholder);
            if (exploreView != null) {
                exploreView.postDelayed(exploreResumeRefresh, 120);
            } else {
                mainHandler.postDelayed(exploreResumeRefresh, 120);
            }
        }
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(exploreResumeRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (safetyPageController != null) {
            safetyPageController.destroy();
            safetyPageController = null;
        }
        super.onDestroy();
    }

    private void selectTab(int tab) {
        selectTab(tab, true);
    }

    private void selectTab(int tab, boolean animate) {
        int previousTab = currentTab;
        if (animate && previousTab == tab) {
            return;
        }
        currentTab = tab;
        BottomBarState.INSTANCE.setSelectedTab(tab);
        if (safetyPageController != null && tab == 1) {
            // 等滑页动画（380ms）结束再刷新/绑定安全页列表，避免行组合创建与动画抢主线程
            safetyPageController.onMaybeShownDelayed(420);
        }
        ensureToolbarVisible();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }

        View targetView = getTabContentView(tab);
        View currentView = getTabContentView(previousTab);
        if (targetView == null) return;

        if (tabSwitchAnimator != null) {
            tabSwitchAnimator.cancel();
            tabSwitchAnimator = null;
        }

        if (!animate || previousTab == tab || currentView == null) {
            showOnlyTabContent(tab);
            return;
        }

        normalizeTabViewsForAnimation(currentView, targetView);

        // 1. 获取屏幕宽度作为完整的滑屏距离，避免重叠堆叠
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final float fromOffset = tab > previousTab ? screenWidth : -screenWidth;
        final float toOffset = -fromOffset;
        final long durationMs = 380L;
        final FastOutSlowInInterpolator tabInterpolator = new FastOutSlowInInterpolator();
        final int targetTab = tab;

        // 2. 重置目标页和当前页的基础属性，去除 scale 和 alpha 的缩放/淡入淡出逻辑
        currentView.setVisibility(View.VISIBLE);
        currentView.setTranslationX(0f);
        // 开启硬件加速以极大提升全屏滑动的流畅度
        currentView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        targetView.bringToFront();
        targetView.setVisibility(View.VISIBLE);
        targetView.setTranslationX(fromOffset);
        targetView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        ensureToolbarVisible();

        // 3. 仅通过 translationX 执行纯净的侧滑动画，解决堆叠问题
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(targetView, View.TRANSLATION_X, fromOffset, 0f),
                ObjectAnimator.ofFloat(currentView, View.TRANSLATION_X, 0f, toOffset)
        );
        animatorSet.setDuration(durationMs);
        animatorSet.setInterpolator(tabInterpolator);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束时必须关闭硬件加速，防止内存泄漏或显示异常
                currentView.setLayerType(View.LAYER_TYPE_NONE, null);
                targetView.setLayerType(View.LAYER_TYPE_NONE, null);
                
                resetTabViewTransform(currentView);
                resetTabViewTransform(targetView);
                if (!cancelled && currentTab == targetTab) {
                    showOnlyTabContent(targetTab);
                }
                tabSwitchAnimator = null;
            }
        });
        tabSwitchAnimator = animatorSet;
        animatorSet.start();
    }

    private View getTabContentView(int tab) {
        switch (tab) {
            case 0:
                return findViewById(R.id.contentDevice);
            case 1:
                return findViewById(R.id.contentHome);
            case 2:
                return findViewById(R.id.fragmentExplorePlaceholder);
            case 3:
                return findViewById(R.id.fragmentSettingsPlaceholder);
            default:
                return null;
        }
    }

    private void showOnlyTabContent(int tab) {
        View[] views = new View[]{
                findViewById(R.id.contentHome),
                findViewById(R.id.contentDevice),
                findViewById(R.id.fragmentExplorePlaceholder),
                findViewById(R.id.fragmentSettingsPlaceholder)
        };
        for (View view : views) {
            if (view == null) continue;
            resetTabViewTransform(view);
            view.setVisibility(View.GONE);
        }

        View selectedView = getTabContentView(tab);
        if (selectedView != null) {
            selectedView.setVisibility(View.VISIBLE);
            selectedView.bringToFront();
        }
        ensureToolbarVisible();

        // 切到“玩机”(Explore) 时：静默检测并自动安装核心模块（只触发一次）
        if (tab == 2) {
            maybeAutoInstallCoreModuleSilently();
        }
    }

    private void maybeAutoInstallCoreModuleSilently() {
        if (exploreAutoInstallTriggered || exploreAutoInstallRunning) {
            return;
        }
        exploreAutoInstallTriggered = true;
        exploreAutoInstallRunning = true;

        new Thread(() -> {
            try {
                if (!canExecuteSuAsRoot()) {
                    return;
                }
                if (isCoreModulePresentViaSu()) {
                    return;
                }

                File zip = extractAssetToCache("violet_box_module.zip", "violet_box_module_autoinstall.zip");
                if (zip == null || !zip.exists()) {
                    return;
                }

                // 静默执行：4 种安装命令依次尝试（成功即停止）
                String p = shellEscape(zip.getAbsolutePath());
                String[] candidates = new String[]{
                        "magisk --install-module " + p,
                        "/data/adb/magisk/magisk --install-module " + p,
                        "ksud module install " + p,
                        "apd module install " + p
                };
                for (String cmd : candidates) {
                    if (runSuCommand(cmd) == 0) {
                        break;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                exploreAutoInstallRunning = false;
            }
        }).start();
    }

    private boolean isCoreModulePresentViaSu() {
        // 兼容已安装与待更新目录
        String check = "[ -d /data/adb/modules/violet_box_module ] || [ -d /data/adb/modules_update/violet_box_module ]";
        return runSuCommand(check) == 0;
    }

    private File extractAssetToCache(String assetName, String outName) {
        try {
            File out = new File(getCacheDir(), outName);
            try (InputStream is = getAssets().open(assetName);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int runSuCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(25, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return -1;
            }
            return process.exitValue();
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String shellEscape(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void normalizeTabViewsForAnimation(View currentView, View targetView) {
        View[] views = new View[]{
                findViewById(R.id.contentHome),
                findViewById(R.id.contentDevice),
                findViewById(R.id.fragmentExplorePlaceholder),
                findViewById(R.id.fragmentSettingsPlaceholder)
        };
        for (View view : views) {
            if (view == null) continue;
            resetTabViewTransform(view);
            if (view == currentView || view == targetView) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(1f);
            } else {
                view.setAlpha(1f);
                view.setVisibility(View.GONE);
            }
        }
    }

    private float resolveTabAnimationDistance(View currentView, View targetView) {
        int width = 0;
        if (targetView != null) {
            width = targetView.getWidth();
        }
        if (width <= 0 && currentView != null) {
            width = currentView.getWidth();
        }
        if (width <= 0 && nativeContentView != null) {
            width = nativeContentView.getWidth();
        }
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(width * 0.32f, dpToPx(104));
    }

    private void resetTabViewTransform(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private void ensureToolbarVisible() {
        if (appBarLayout != null) {
            appBarLayout.setVisibility(View.VISIBLE);
            appBarLayout.bringToFront();
        }
    }

    /** 打开内置应用管理（列表、提取 APK、卸载、Root 冻结与备份等）。 */
    private void openSystemAppManagement() {
        startActivity(new Intent(this, AppManagerActivity.class));
    }

    private void setViewVisibilitySafe(int viewId, int visibility) {
        View v = findViewById(viewId);
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    private void showQuickRebootConfirmDialog(String title, String command) {
        if (!canExecuteSuAsRoot()) {
            Toast.makeText(this, "请先授予应用ROOT权限", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage("即将执行重启操作，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> executeQuickRebootCommand(command))
                .show();
    }

    private void executeQuickRebootCommand(String command) {
        Thread t = new Thread(() -> {
            String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
            boolean success = false;
            for (String suBin : suBins) {
                Process process = null;
                try {
                    process = new ProcessBuilder(suBin, "-c", command).redirectErrorStream(true).start();
                    boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
                    if (finished && process.exitValue() == 0) {
                        success = true;
                        break;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (process != null) {
                        process.destroy();
                    }
                }
            }
            if (!success) {
                runOnUiThread(() -> Toast.makeText(this, "执行失败：请检查ROOT权限或设备支持情况", Toast.LENGTH_SHORT).show());
            }
        });
        t.start();
    }

    private void resetAllTabs() {
        int defaultColor = ContextCompat.getColor(this, R.color.nav_item_text);
        
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
        int itemId = item.getItemId();
        if (itemId == R.id.action_overflow_quick_reboot) {
            View anchor = findViewById(R.id.action_overflow_quick_reboot);
            if (anchor == null) {
                Toolbar toolbar = findViewById(R.id.toolbar);
                if (toolbar != null) {
                    anchor = toolbar;
                }
            }
            showQuickRebootPopupMenu(anchor);
            return true;
        }
        if (itemId == R.id.action_quick_reboot_system) {
            showQuickRebootConfirmDialog("重启系统", "reboot");
            return true;
        }
        if (itemId == R.id.action_quick_reboot_bootloader) {
            showQuickRebootConfirmDialog("重启到 Bootloader", "reboot bootloader");
            return true;
        }
        if (itemId == R.id.action_quick_reboot_fastbootd) {
            showQuickRebootConfirmDialog("重启到 FastbootD", "reboot fastboot");
            return true;
        }
        if (itemId == R.id.action_quick_reboot_recovery) {
            showQuickRebootConfirmDialog("重启到 Recovery", "reboot recovery");
            return true;
        }
        if (itemId == R.id.action_quick_reboot_edl) {
            showQuickRebootConfirmDialog("重启到 EDL", "reboot edl");
            return true;
        }
        if (itemId == R.id.action_quick_reboot_safe_mode) {
            showQuickRebootConfirmDialog("重启到安全模式", "setprop persist.sys.safemode 1; reboot");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showQuickRebootPopupMenu(View anchor) {
        if (anchor == null) return;
        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.END, 0, R.style.ThemeOverlay_Violet_ToolbarPopup);
        popupMenu.getMenuInflater().inflate(R.menu.quick_reboot_menu, popupMenu.getMenu());
        applyPopupMenuOffsetCompat(popupMenu, -dpToPx(22));
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_quick_reboot_system) {
                showQuickRebootConfirmDialog("重启系统", "reboot");
                return true;
            }
            if (itemId == R.id.action_quick_reboot_bootloader) {
                showQuickRebootConfirmDialog("重启到 Bootloader", "reboot bootloader");
                return true;
            }
            if (itemId == R.id.action_quick_reboot_fastbootd) {
                showQuickRebootConfirmDialog("重启到 FastbootD", "reboot fastboot");
                return true;
            }
            if (itemId == R.id.action_quick_reboot_recovery) {
                showQuickRebootConfirmDialog("重启到 Recovery", "reboot recovery");
                return true;
            }
            if (itemId == R.id.action_quick_reboot_edl) {
                showQuickRebootConfirmDialog("重启到 EDL", "reboot edl");
                return true;
            }
            if (itemId == R.id.action_quick_reboot_safe_mode) {
                showQuickRebootConfirmDialog("重启到安全模式", "setprop persist.sys.safemode 1; reboot");
                return true;
            }
            return false;
        });
        popupMenu.setOnDismissListener(menu -> {
            quickRebootPopupMenu = null;
            setOverflowMenuScrimVisible(false);
        });
        quickRebootPopupMenu = popupMenu;
        setOverflowMenuScrimVisible(true);
        popupMenu.show();
    }

    private void applyPopupMenuOffsetCompat(PopupMenu popupMenu, int verticalOffsetPx) {
        if (popupMenu == null) return;
        try {
            java.lang.reflect.Field popupField = PopupMenu.class.getDeclaredField("mPopup");
            popupField.setAccessible(true);
            Object helper = popupField.get(popupMenu);
            if (helper == null) return;
            java.lang.reflect.Method setVerticalOffsetMethod = helper.getClass().getMethod("setVerticalOffset", int.class);
            setVerticalOffsetMethod.invoke(helper, verticalOffsetPx);
        } catch (Exception ignored) {
            // 兼容不同 appcompat 版本，偏移失败时保持默认位置
        }
    }

    private void setOverflowMenuScrimVisible(boolean visible) {
        if (overflowMenuScrim == null) return;
        overflowMenuScrim.animate().cancel();
        if (visible) {
            overflowMenuScrim.bringToFront();
            overflowMenuScrim.setAlpha(0f);
            overflowMenuScrim.setVisibility(View.VISIBLE);
            overflowMenuScrim.animate().alpha(1f).setDuration(160).start();
        } else {
            overflowMenuScrim.animate()
                    .alpha(0f)
                    .setDuration(140)
                    .withEndAction(() -> overflowMenuScrim.setVisibility(View.GONE))
                    .start();
        }
    }

    private void updateExploreRootStatus(boolean forceRefresh) {
        TextView tvExploreRootStatus = findViewById(R.id.tvExploreRootStatus);
        TextView tvExploreRootManager = findViewById(R.id.tvExploreRootManager);
        ImageView ivExploreRootStatusIcon = findViewById(R.id.ivExploreRootStatusIcon);
        MaterialCardView cardExploreRootStatusIconBg = findViewById(R.id.cardExploreRootStatusIconBg);
        if (tvExploreRootStatus == null || tvExploreRootManager == null || ivExploreRootStatusIcon == null || cardExploreRootStatusIconBg == null) {
            return;
        }

        ExploreRootStatus cached = cachedExploreRootStatus;
        long cacheAge = SystemClock.elapsedRealtime() - cachedExploreRootStatusAt;
        if (cached != null) {
            renderExploreRootStatus(cached);
        } else {
            renderExploreRootStatusLoading();
        }

        if (!forceRefresh && cached != null && cacheAge < EXPLORE_ROOT_STATUS_CACHE_MS) {
            return;
        }
        if (exploreRootStatusRefreshRunning) {
            return;
        }

        exploreRootStatusRefreshRunning = true;
        new Thread(() -> {
            ExploreRootStatus resolved = resolveExploreRootStatus();
            cachedExploreRootStatus = resolved;
            cachedExploreRootStatusAt = SystemClock.elapsedRealtime();
            exploreRootStatusRefreshRunning = false;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                renderExploreRootStatus(resolved);
            });
        }, "explore-root-status").start();
    }

    private ExploreRootStatus resolveExploreRootStatus() {
        boolean rootGranted = canExecuteSuAsRoot();
        String manager = rootGranted ? detectGrantedRootManagerByAdbFeatures() : "";
        return new ExploreRootStatus(rootGranted, manager);
    }

    private void renderExploreRootStatusLoading() {
        TextView tvExploreRootStatus = findViewById(R.id.tvExploreRootStatus);
        TextView tvExploreRootManager = findViewById(R.id.tvExploreRootManager);
        ImageView ivExploreRootStatusIcon = findViewById(R.id.ivExploreRootStatusIcon);
        MaterialCardView cardExploreRootStatusIconBg = findViewById(R.id.cardExploreRootStatusIconBg);
        if (tvExploreRootStatus == null || tvExploreRootManager == null || ivExploreRootStatusIcon == null || cardExploreRootStatusIconBg == null) {
            return;
        }
        tvExploreRootStatus.setText("正在检测ROOT状态...");
        tvExploreRootStatus.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_500));
        tvExploreRootManager.setText("检测完成后将自动刷新");
        tvExploreRootManager.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_500));
        ivExploreRootStatusIcon.setImageResource(R.drawable.ic_ms_security);
        ivExploreRootStatusIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.explore_slate_500)));
        cardExploreRootStatusIconBg.setCardBackgroundColor(ContextCompat.getColor(this, R.color.explore_slate_100));
    }

    private void renderExploreRootStatus(ExploreRootStatus status) {
        TextView tvExploreRootStatus = findViewById(R.id.tvExploreRootStatus);
        TextView tvExploreRootManager = findViewById(R.id.tvExploreRootManager);
        ImageView ivExploreRootStatusIcon = findViewById(R.id.ivExploreRootStatusIcon);
        MaterialCardView cardExploreRootStatusIconBg = findViewById(R.id.cardExploreRootStatusIconBg);
        if (tvExploreRootStatus == null || tvExploreRootManager == null || ivExploreRootStatusIcon == null || cardExploreRootStatusIconBg == null || status == null) {
            return;
        }

        if (status.rootGranted) {
            tvExploreRootStatus.setText("已授予ROOT");
            tvExploreRootStatus.setTextColor(colorSemanticSafe());
            tvExploreRootManager.setText("Root Manager：" + status.manager);
            ivExploreRootStatusIcon.setImageResource(R.drawable.ic_ms_verified_user);
            ivExploreRootStatusIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.explore_emerald_600)));
            cardExploreRootStatusIconBg.setCardBackgroundColor(ContextCompat.getColor(this, R.color.explore_emerald_100));
        } else {
            tvExploreRootStatus.setText("未授予ROOT");
            tvExploreRootStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_semantic_negative));
            tvExploreRootManager.setText("请授予应用ROOT权限，否则部分功能无法使用");
            ivExploreRootStatusIcon.setImageResource(R.drawable.ic_ms_root_not_granted);
            ivExploreRootStatusIcon.setImageTintList(null);
            cardExploreRootStatusIconBg.setCardBackgroundColor(ContextCompat.getColor(this, R.color.explore_rose_100));
        }
        tvExploreRootManager.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_500));
    }

    private void updateExploreUptime() {
        TextView tvExploreUptimeValue = findViewById(R.id.tvExploreUptimeValue);
        if (tvExploreUptimeValue == null) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime();
        long totalMinutes = elapsed / (60 * 1000);
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        String uptimeText;
        if (days > 0) {
            uptimeText = days + "d " + hours + "h";
        } else if (hours > 0) {
            uptimeText = hours + "h " + minutes + "m";
        } else {
            uptimeText = minutes + "m";
        }
        tvExploreUptimeValue.setText(uptimeText);
    }

    private void hideNewVersionCard() {
        View card = findViewById(R.id.cardNewVersion);
        if (card != null) {
            card.setVisibility(View.GONE);
        }
    }

    private void checkVersionUpdate(boolean showToast) {
        Thread t = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_JSON_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setUseCaches(false);

                int code = connection.getResponseCode();
                if (code != 200) {
                    if (showToast) {
                        runOnUiThread(() -> Toast.makeText(this, "检测失败：服务器响应异常", Toast.LENGTH_SHORT).show());
                    }
                    runOnUiThread(this::hideNewVersionCard);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject json = new JSONObject(sb.toString());
                String latestVersion = json.optString("version", "");
                String downloadUrl = json.optString("downloadUrl", "");
                if (latestVersion.isEmpty() || downloadUrl.isEmpty()) {
                    if (showToast) {
                        runOnUiThread(() -> Toast.makeText(this, "检测失败：更新配置无效", Toast.LENGTH_SHORT).show());
                    }
                    runOnUiThread(this::hideNewVersionCard);
                    return;
                }

                String currentVersion = getAppVersionName();
                if (isRemoteVersionNewer(currentVersion, latestVersion)) {
                    runOnUiThread(() -> {
                        showNewVersionCard(latestVersion, downloadUrl);
                        if (showToast) {
                            Toast.makeText(this, "发现新版本：" + latestVersion, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        hideNewVersionCard();
                        if (showToast) {
                            Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception ignored) {
                if (showToast) {
                    runOnUiThread(() -> Toast.makeText(this, "检测失败：网络或解析异常", Toast.LENGTH_SHORT).show());
                }
                runOnUiThread(this::hideNewVersionCard);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        t.start();
    }

    private void showNewVersionCard(String latestVersion, String downloadUrl) {
        View card = findViewById(R.id.cardNewVersion);
        TextView tvDesc = findViewById(R.id.tvNewVersionDesc);
        TextView tvAction = findViewById(R.id.tvNewVersionAction);
        if (card == null || tvDesc == null || tvAction == null) {
            return;
        }
        tvDesc.setText("最新版本：" + latestVersion);
        View.OnClickListener openDownload = v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开下载链接", Toast.LENGTH_SHORT).show();
            }
        };
        card.setOnClickListener(openDownload);
        tvAction.setOnClickListener(openDownload);
        card.setVisibility(View.VISIBLE);
    }

    private String getAppVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    private boolean isRemoteVersionNewer(String currentVersion, String latestVersion) {
        int[] current = parseVersion(currentVersion);
        int[] latest = parseVersion(latestVersion);
        int max = Math.max(current.length, latest.length);
        for (int i = 0; i < max; i++) {
            int c = i < current.length ? current[i] : 0;
            int l = i < latest.length ? latest[i] : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private int[] parseVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return new int[]{0};
        }
        String normalized = version.trim().replaceAll("[^0-9.]", "");
        if (normalized.isEmpty()) {
            return new int[]{0};
        }
        String[] parts = normalized.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (Exception ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    private boolean canExecuteSuAsRoot() {
        String[][] commands = new String[][]{
                {"su", "-c", "id"},
                {"/system/bin/su", "-c", "id"},
                {"/system/xbin/su", "-c", "id"}
        };
        for (String[] command : commands) {
            Process process = null;
            try {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroy();
                    continue;
                }
                if (process.exitValue() != 0) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && line.contains("uid=0")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return false;
    }

    private String detectGrantedRootManagerByAdbFeatures() {
        boolean hasKernelSu = existsPathViaSu("/data/adb/ksud") || existsPathViaSu("/data/adb/ksd");
        boolean hasAPatch = existsPathViaSu("/data/adb/apd");
        boolean hasMagisk = existsPathViaSu("/data/adb/magisk.db");

        List<String> managers = new ArrayList<>();
        if (hasKernelSu) {
            managers.add("KernelSU");
        }
        if (hasAPatch) {
            managers.add("APatch");
        }
        if (hasMagisk) {
            managers.add("Magisk");
        }

        if (managers.isEmpty()) {
            return "未知（未匹配到 /data/adb 特征）";
        }
        return String.join(" & ", managers);
    }

    private boolean existsPathViaSu(String absolutePath) {
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "[ -e \"" + absolutePath + "\" ]")
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (finished && process.exitValue() == 0) {
                    return true;
                }
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return false;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

}
