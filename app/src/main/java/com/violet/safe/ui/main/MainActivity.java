package com.violet.safe.ui.main;

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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Toast;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import java.io.FileOutputStream;
import java.io.InputStream;

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
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

import com.scottyab.rootbeer.RootBeer;
import com.violet.safe.core.util.SelinuxStatusReader;
import com.violet.safe.data.detector.RootDetector;
import com.violet.safe.ui.appmanager.AppManagerActivity;
import com.violet.safe.ui.about.AboutActivity;
import com.violet.safe.ui.font.FontLibraryBackupActivity;
import com.violet.safe.ui.module.ModuleManagerActivity;
import com.violet.safe.ui.partition.PartitionManagerActivity;
import com.violet.safe.ui.payload.PayloadActivity;
import com.violet.safe.ui.plugin.GlobalDeviceSpoofActivity;
import com.violet.safe.ui.plugin.DeviceIdModifyActivity;
import com.violet.safe.ui.plugin.VioletPluginActivity;
import com.violet.safe.ui.selinux.SelinuxManagerActivity;

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

import com.violet.safe.R;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_TAB = "extra_open_tab";

    private static final String PREFS_NAME = "violet_detection_logs";
    private static final String KEY_LOG_PATH = "log_path";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String UPDATE_JSON_URL = "https://gitee.com/smartpaocai/smart-tool/raw/master/violetbox.json";
    private static final String CURRENT_VERSION_NAME = "1.0.0";

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
    private View overflowMenuScrim;
    private PopupMenu quickRebootPopupMenu;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private AnimatorSet tabSwitchAnimator;

    private View nativeContentView;
    private volatile boolean exploreAutoInstallTriggered = false;
    private volatile boolean exploreAutoInstallRunning = false;

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
            getSupportActionBar().setTitle("紫罗兰Box");
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
        androidx.appcompat.widget.SwitchCompat switchCheckUpdate = findViewById(R.id.switchCheckUpdate);
        View cardCheckUpdate = findViewById(R.id.cardCheckUpdate);
        SharedPreferences updatePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean autoCheckUpdate = updatePrefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
        if (switchCheckUpdate != null) {
            // iOS7/26 风格：thumb/track 固定尺寸（不受素材 intrinsic 大小影响）
            try {
                Drawable thumb = getDrawable(R.drawable.switch_ios26_thumb);
                Drawable track = getDrawable(R.drawable.switch_ios26_track);
                if (thumb != null) {
                    // Switch 内部可能给 thumb 非正方形 bounds，这里强制用正方形居中绘制保证“正圆”
                    switchCheckUpdate.setThumbDrawable(new FixedSizeDrawable(thumb, dpToPx(24), dpToPx(24), true));
                }
                if (track != null) {
                    // iOS 风格常见比例：约 44x28
                    switchCheckUpdate.setTrackDrawable(new FixedSizeDrawable(track, dpToPx(44), dpToPx(28), false));
                }
                switchCheckUpdate.setSplitTrack(false);
            } catch (Exception ignored) {
            }
            switchCheckUpdate.setChecked(autoCheckUpdate);
            switchCheckUpdate.setOnCheckedChangeListener((buttonView, isChecked) ->
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

        clearLog();
        setupDeviceInfo();
        updateExploreRootStatus();
        updateExploreUptime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 返回前台后刷新首页 Explore 的 ROOT 实时状态
        updateExploreRootStatus();
        updateExploreUptime();
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
        ensureToolbarVisible();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("紫罗兰Box");
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
        // 设备页 UI 由 DeviceFragment + fragment_device.xml 绑定；此处仅写日志与首页检测逻辑
        String selinuxStatus = getSELinuxStatus();
        appendLog(buildSelinuxDiagnostics(selinuxStatus));
        String bootSlot = getBootSlot();
        appendLog("启动槽位 = " + bootSlot);

        setupRootDetection();
        updateExploreRootStatus();
    }

    private void updateExploreRootStatus() {
        TextView tvExploreRootStatus = findViewById(R.id.tvExploreRootStatus);
        TextView tvExploreRootManager = findViewById(R.id.tvExploreRootManager);
        ImageView ivExploreRootStatusIcon = findViewById(R.id.ivExploreRootStatusIcon);
        MaterialCardView cardExploreRootStatusIconBg = findViewById(R.id.cardExploreRootStatusIconBg);
        if (tvExploreRootStatus == null || tvExploreRootManager == null || ivExploreRootStatusIcon == null || cardExploreRootStatusIconBg == null) {
            return;
        }

        RootBeer rootBeer = new RootBeer(this);
        RootDetector advancedDetector = new RootDetector(this);
        boolean hasRootArtifacts = rootBeer.isRooted() || advancedDetector.isDeviceRooted();
        boolean rootGranted = hasRootArtifacts && canExecuteSuAsRoot();
        if (rootGranted) {
            String manager = detectGrantedRootManagerByAdbFeatures();
            tvExploreRootStatus.setText("已授予ROOT");
            tvExploreRootStatus.setTextColor(colorSemanticSafe());
            tvExploreRootManager.setText("Root Manager：" + manager);
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
        return CURRENT_VERSION_NAME;
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

    private void setupRootDetection() {
        totalDetectionRiskCount = 0;
        environmentRiskCount = 0;
        riskAppsDetectionPoint = 0;

        clearLog();
        checkRiskApps();
        appendLog("开始执行 Root 与安全检测");

        android.widget.LinearLayout layoutRootDetails = findViewById(R.id.layoutRootDetails);
        android.widget.LinearLayout layoutAdvancedDetails = findViewById(R.id.layoutAdvancedDetails);
        TextView tvBootloader = findViewById(R.id.tvBootloader);
        TextView tvUsbDebug = findViewById(R.id.tvUsbDebug);
        if (layoutRootDetails == null || layoutAdvancedDetails == null
                || tvBootloader == null || tvUsbDebug == null) {
            appendLog("安全检测 UI 缺失，已跳过明细渲染");
            return;
        }

        layoutRootDetails.removeAllViews();
        layoutAdvancedDetails.removeAllViews();

        try {
            runRootDetectionBody(layoutRootDetails, layoutAdvancedDetails, tvBootloader, tvUsbDebug);
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "setupRootDetection", t);
            appendLog("安全检测异常: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            layoutRootDetails.removeAllViews();
            layoutAdvancedDetails.removeAllViews();
            totalDetectionRiskCount = 0;
            environmentRiskCount = riskAppsDetectionPoint;
            updateEnvironmentSummary();
        }
    }

    private void runRootDetectionBody(android.widget.LinearLayout layoutRootDetails,
                                      android.widget.LinearLayout layoutAdvancedDetails,
                                      TextView tvBootloader,
                                      TextView tvUsbDebug) {
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

        RootDetector advancedDetector = new RootDetector(this);
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

        ViewGroup.LayoutParams lpRaw = tv.getLayoutParams();
        if (lpRaw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) lpRaw;
            lp.topMargin = dpToPx(6);
            lp.bottomMargin = dpToPx(10);
            tv.setLayoutParams(lp);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FF4D4F);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0x33FF4D4F);
        tv.setBackground(bg);
    }

    private void styleInlineWarningItem(TextView tv) {
        tv.setTextColor(ContextCompat.getColor(this, R.color.ios_text_primary));
        tv.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        ViewGroup.LayoutParams lpRaw = tv.getLayoutParams();
        if (lpRaw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) lpRaw;
            lp.topMargin = dpToPx(6);
            lp.bottomMargin = dpToPx(10);
            tv.setLayoutParams(lp);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FFB74D);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0x33FF9800);
        tv.setBackground(bg);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class FixedSizeDrawable extends Drawable implements Drawable.Callback {
        private final Drawable wrapped;
        private final int w;
        private final int h;
        private final boolean forceSquareCenter;

        FixedSizeDrawable(Drawable wrapped, int widthPx, int heightPx, boolean forceSquareCenter) {
            this.wrapped = wrapped;
            this.w = widthPx;
            this.h = heightPx;
            this.forceSquareCenter = forceSquareCenter;
            // 让内部 drawable 的 invalidate/schedule 能回调到外层
            this.wrapped.setCallback(this);
        }

        @Override
        public void draw(Canvas canvas) {
            wrapped.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            if (!forceSquareCenter) {
                wrapped.setBounds(bounds);
                return;
            }
            int size = Math.min(bounds.width(), bounds.height());
            int left = bounds.left + (bounds.width() - size) / 2;
            int top = bounds.top + (bounds.height() - size) / 2;
            wrapped.setBounds(left, top, left + size, top + size);
        }

        @Override
        public void setAlpha(int alpha) {
            wrapped.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            wrapped.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean isStateful() {
            return wrapped.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed = wrapped.setState(state);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean changed = wrapped.setLevel(level);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }

        @Override
        public Drawable mutate() {
            wrapped.mutate();
            return this;
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduleSelf(what);
        }

        @Override
        public int getIntrinsicWidth() {
            return w;
        }

        @Override
        public int getIntrinsicHeight() {
            return h;
        }
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

    }

    private void checkRiskApps() {
        TextView tvRiskApps = findViewById(R.id.tvRiskApps);
        if (tvRiskApps == null) {
            appendLog("风险应用: UI 缺失，已跳过");
            return;
        }

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

    private String getSELinuxStatus() {
        return SelinuxStatusReader.getStatusString();
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
        String enforceVal = SelinuxStatusReader.readEnforceNodeOrNull();
        String getenforceVal = SelinuxStatusReader.readEnforceViaGetenforce();
        Boolean enabled = SelinuxStatusReader.readEnabledFromStatusNode();
        String apiVal = SelinuxStatusReader.readApiModeOrNull();
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
