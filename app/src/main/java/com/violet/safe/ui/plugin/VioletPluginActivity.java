package com.violet.safe.ui.plugin;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.animation.ObjectAnimator;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.violet.safe.R;
import com.violet.safe.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VioletPluginActivity extends AppCompatActivity {

    private static final String TAG = "HiddenAppListDetect";
    private static final String TRICKY_STORE_DIR = "/data/adb/tricky_store";
    private static final String[] LSPOSED_MODULE_DIR_CANDIDATES = new String[]{
            "/data/adb/modules/lsposed",
            "/data/adb/modules/zygisk_lsposed",
            "/data/adb/modules/riru_lsposed",
            "/data/adb/modules/lspd"
    };
    private static final String[] LSPOSED_CONFIG_FILE_CANDIDATES = new String[]{
            "/data/adb/lspd/config/modules.list",
            "/data/adb/lspd/config/modules.json",
            "/data/adb/lspd/config/modules.xml",
            "/data/adb/lspd/config/modules_config.xml",
            "/data/adb/lspd/config/modules.conf",
            "/data/adb/lspd/config/modules",
            "/data/adb/lspd/config/config.json",
            "/data/adb/lspd/config/config.xml"
    };
    private static final String[] LSPOSED_DB_CANDIDATES = new String[]{
            "/data/adb/lspd/config/modules_config.db-wal",
            "/data/adb/lspd/config/modules_config.db"
    };
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+");
    private static final String PREFS_HIDDEN_APP_LIST = "hidden_app_list_detector";
    private static final String KEY_FINGERPRINT_CERT_SHA256 = "fingerprint_cert_sha256";
    private static final String KEY_FINGERPRINT_XPOSED_INIT_SHA256 = "fingerprint_xposed_init_sha256";
    private static final String KEY_FINGERPRINT_DISPLAY_NAME = "fingerprint_display_name";
    // 插件页的多个模块检测需要并发执行，避免某个 su/IO 检测拖慢其它模块的加载。
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3);
    private View cardTrickyStoreModule;
    private View cardHiddenAppList;
    private View cardKernelDisguise;
    private TextView tvHiddenAppListMeta;
    private ImageView ivHiddenAppListLoading;
    private ObjectAnimator hiddenAppListLoadingAnimator;
    private volatile DetectionResult lastHiddenAppListResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_violet_plugin);

        Toolbar toolbar = findViewById(R.id.toolbarVioletPlugin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("紫罗兰插件");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        cardHiddenAppList = findViewById(R.id.cardHiddenAppList);
        tvHiddenAppListMeta = findViewById(R.id.tvHiddenAppListMeta);
        ivHiddenAppListLoading = findViewById(R.id.ivHiddenAppListLoading);
        if (cardHiddenAppList != null) {
            cardHiddenAppList.setOnClickListener(v -> {
                DetectionResult cached = lastHiddenAppListResult;
                if (cached == null) {
                    Toast.makeText(this, "正在检测隐藏应用列表，请稍后…", Toast.LENGTH_SHORT).show();
                    detectHiddenAppListModule();
                    return;
                }
                if (!cached.likelyHiddenAppListModuleDetected) {
                    if (cached.ambiguousCandidates != null && !cached.ambiguousCandidates.isEmpty()) {
                        showBindModuleDialog(cached.ambiguousCandidates);
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("无法进入")
                            .setMessage("未检测到“隐藏应用列表”模块。\n\n请先安装对应应用后再进入。")
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                if (cached.configJsonPath == null || cached.configJsonPath.trim().isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("配置缺失")
                            .setMessage("已检测到模块，但未找到 config.json。\n\n可能原因：未授权 su / 模块版本不一致 / 配置未生成。")
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                Intent intent = new Intent(this, HiddenAppListConfigActivity.class);
                intent.putExtra(HiddenAppListConfigActivity.EXTRA_MODULE_PACKAGE, cached.bestPackageName);
                intent.putExtra(HiddenAppListConfigActivity.EXTRA_MODULE_NAME, cached.displayName);
                intent.putExtra(HiddenAppListConfigActivity.EXTRA_CONFIG_PATH, cached.configJsonPath);
                startActivity(intent);
            });
        }
        detectHiddenAppListModule();

        cardTrickyStoreModule = findViewById(R.id.cardTrickyStoreModule);
        detectTrickyStoreModule();

        cardKernelDisguise = findViewById(R.id.cardKernelDisguise);
        if (cardKernelDisguise != null) {
            cardKernelDisguise.setOnClickListener(v ->
                    startActivity(new Intent(this, KernelDisguiseActivity.class)));
        }
    }

    @Override
    protected void onDestroy() {
        stopHiddenAppListLoading();
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void detectTrickyStoreModule() {
        ioExecutor.execute(() -> {
            boolean installed = isDirectoryExistsViaSu(TRICKY_STORE_DIR);
            runOnUiThread(() -> updateModuleUi(installed));
        });
    }

    private void detectHiddenAppListModule() {
        if (tvHiddenAppListMeta == null) {
            return;
        }
        tvHiddenAppListMeta.setText("状态：检测中…");
        tvHiddenAppListMeta.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_500));
        startHiddenAppListLoading();
        ioExecutor.execute(() -> {
            DetectionResult result = detectLikelyHiddenAppListXposedModule();
            runOnUiThread(() -> {
                if (tvHiddenAppListMeta == null) return;
                lastHiddenAppListResult = result;
                stopHiddenAppListLoading();
                applyHiddenAppListStatus(tvHiddenAppListMeta, result);
                tvHiddenAppListMeta.setOnClickListener(null);
            });
        });
    }

    private void applyInstallStatusPill(TextView target, boolean installed) {
        if (target == null) return;
        target.setText(installed ? "已安装" : "未安装");
        int color = ContextCompat.getColor(
                this,
                installed ? R.color.ios_semantic_positive : R.color.ios_semantic_negative
        );
        target.setTextColor(color);
    }

    private void applyHiddenAppListStatus(TextView target, DetectionResult result) {
        if (target == null || result == null) return;
        if (!result.likelyHiddenAppListModuleDetected) {
            applyDualStatusText(target, "未安装", false, "未激活", false);
            return;
        }
        if (!result.lsposedInstalled) {
            applyDualStatusText(target, "已安装", true, "未激活", false);
            return;
        }
        boolean enabled = result.likelyModuleEnabled;
        applyDualStatusText(target, "已安装", true, enabled ? "已激活" : "未激活", enabled);
    }

    private void applyDualStatusText(
            TextView target,
            String installLabel,
            boolean installPositive,
            String activeLabel,
            boolean activePositive
    ) {
        if (target == null) return;
        String text = installLabel + " " + activeLabel;
        SpannableString spannable = new SpannableString(text);
        int positive = ContextCompat.getColor(this, R.color.ios_semantic_positive);
        int negative = ContextCompat.getColor(this, R.color.ios_semantic_negative);
        spannable.setSpan(
                new ForegroundColorSpan(installPositive ? positive : negative),
                0,
                installLabel.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        int activeStart = installLabel.length() + 1;
        spannable.setSpan(
                new ForegroundColorSpan(activePositive ? positive : negative),
                activeStart,
                activeStart + activeLabel.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        target.setText(spannable);
    }

    private void startHiddenAppListLoading() {
        if (ivHiddenAppListLoading == null) {
            return;
        }
        ivHiddenAppListLoading.setVisibility(View.VISIBLE);
        if (hiddenAppListLoadingAnimator == null) {
            hiddenAppListLoadingAnimator = ObjectAnimator.ofFloat(ivHiddenAppListLoading, View.ROTATION, 0f, 360f);
            hiddenAppListLoadingAnimator.setDuration(900);
            hiddenAppListLoadingAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            hiddenAppListLoadingAnimator.setInterpolator(new LinearInterpolator());
        }
        if (!hiddenAppListLoadingAnimator.isRunning()) {
            hiddenAppListLoadingAnimator.start();
        }
    }

    private void stopHiddenAppListLoading() {
        if (ivHiddenAppListLoading == null) {
            return;
        }
        if (hiddenAppListLoadingAnimator != null) {
            hiddenAppListLoadingAnimator.cancel();
        }
        ivHiddenAppListLoading.setRotation(0f);
        ivHiddenAppListLoading.setVisibility(View.GONE);
    }

    private DetectionResult detectLikelyHiddenAppListXposedModule() {
        DetectionResult out = new DetectionResult();
        Set<String> hintedPackages = readEnabledLsposedModulePackagesViaSu();
        out.lsposedInstalled = isLikelyLsposedInstalledViaSu(hintedPackages, new HashSet<>());
        Log.d(TAG, "lsposedInstalled=" + out.lsposedInstalled
                + ", hintedPackagesCount=" + hintedPackages.size()
                + ", strictEnabledPackagesCount=deferred");

        PackageManager pm = getPackageManager();
        if (pm == null) {
            return out;
        }
        List<PackageInfo> installed;
        try {
            installed = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        } catch (Throwable t) {
            return out;
        }
        if (installed == null || installed.isEmpty()) {
            return out;
        }

        String pinnedCert = getSharedPreferences(PREFS_HIDDEN_APP_LIST, MODE_PRIVATE)
                .getString(KEY_FINGERPRINT_CERT_SHA256, "");
        String pinnedInit = getSharedPreferences(PREFS_HIDDEN_APP_LIST, MODE_PRIVATE)
                .getString(KEY_FINGERPRINT_XPOSED_INIT_SHA256, "");

        int xposedCount = 0;
        Candidate best = null;
        Candidate pinned = null;
        Candidate configMatched = null;
        List<Candidate> candidatesForBinding = new ArrayList<>();
        for (PackageInfo pi : installed) {
            if (pi == null || pi.applicationInfo == null) continue;
            ApplicationInfo ai = pi.applicationInfo;
            Bundle md = ai.metaData;
            boolean isXposedModule = md != null
                    && (md.getBoolean("xposedmodule", false)
                    || md.containsKey("xposedmodule")
                    || md.containsKey("xposeddescription")
                    || md.containsKey("xposedminversion"));
            if (!isXposedModule) {
                continue;
            }
            xposedCount++;

            String pkg = pi.packageName == null ? "" : pi.packageName;

            // 已经命中隐藏应用列表后，不再对其它模块做额外的文件/目录探测（该模块只会存在一个）
            if (configMatched != null) {
                continue;
            }

            String label = safeGetAppLabel(pm, ai);
            String desc = resolveXposedDescription(pm, ai, md);
            // 安装识别阶段不跑严格激活检测，仅用轻量 hint 做评分辅助。
            boolean enabled = !pkg.isEmpty() && hintedPackages.contains(pkg);
            if (!pkg.isEmpty()) {
                Log.d(TAG, "candidate pkg=" + pkg + ", enabledByHint=" + enabled);
            }

            String certSha256 = getSigningCertSha256(pm, pkg);
            String xposedInitSha256 = getXposedInitSha256(ai == null ? "" : ai.sourceDir);
            String dataDir = ai == null ? "" : (ai.dataDir == null ? "" : ai.dataDir);
            ConfigHit configHit = !pkg.isEmpty() ? findHiddenAppListConfigViaSu(pkg, dataDir) : new ConfigHit(false, "", "");
            boolean hasConfig = configHit.hit;

            int score = scoreHiddenAppListCandidate(label, desc, pkg, enabled);
            Candidate c = new Candidate();
            c.packageName = pkg;
            c.displayName = !label.isEmpty() ? label : (!pkg.isEmpty() ? pkg : "未知模块");
            c.enabled = enabled;
            c.score = score;
            c.certSha256 = certSha256;
            c.xposedInitSha256 = xposedInitSha256;
            c.hasConfigJson = hasConfig;
            c.configJsonPath = configHit.path;
            c.configJsonDiagnostic = configHit.diagnostic;

            // 只要是 Xposed 模块，都作为可绑定候选
            candidatesForBinding.add(c);

            // 目录特征强命中：/data/data/<pkg>/files/config.json 或 /data/user/0/<pkg>/files/config.json
            if (configMatched == null && hasConfig) {
                configMatched = c;
                // 命中后不再继续探测其它 XP 模块
                continue;
            }

            // 指纹优先：必中路径（包名怎么改都无所谓，只要签名或 xposed_init 入口类不变）
            if (pinned != null) {
                // already pinned
            } else if (!pinnedCert.isEmpty() && !certSha256.isEmpty() && pinnedCert.equalsIgnoreCase(certSha256)) {
                pinned = c;
            } else if (!pinnedInit.isEmpty() && !xposedInitSha256.isEmpty() && pinnedInit.equalsIgnoreCase(xposedInitSha256)) {
                pinned = c;
            }

            if (score <= 0) {
                continue;
            }
            if (best == null || c.score > best.score) {
                best = c;
            }
        }

        out.anyXposedModuleDetected = xposedCount > 0;
        out.xposedModuleCount = xposedCount;
        if (configMatched != null) {
            Log.d(TAG, "final matched by config json, pkg=" + configMatched.packageName
                    + ", enabled=" + configMatched.enabled);
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = configMatched.displayName;
            out.likelyModuleEnabled = false;
            out.bestPackageName = configMatched.packageName;
            out.bestScore = 1000;
            out.matchedByConfigJson = true;
            out.configJsonPath = configMatched.configJsonPath;
            out.configJsonDiagnostic = configMatched.configJsonDiagnostic;
        } else if (pinned != null) {
            Log.d(TAG, "final matched by fingerprint, pkg=" + pinned.packageName
                    + ", enabled=" + pinned.enabled);
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = pinned.displayName;
            out.likelyModuleEnabled = false;
            out.bestPackageName = pinned.packageName;
            out.bestScore = 999;
            out.configJsonPath = pinned.configJsonPath;
            out.configJsonDiagnostic = pinned.configJsonDiagnostic;
        } else if (best != null && best.score >= 3) {
            Log.d(TAG, "final matched by score, pkg=" + best.packageName
                    + ", score=" + best.score + ", enabled=" + best.enabled);
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = best.displayName;
            out.likelyModuleEnabled = false;
            out.bestPackageName = best.packageName;
            out.bestScore = best.score;
            out.configJsonPath = best.configJsonPath;
            out.configJsonDiagnostic = best.configJsonDiagnostic;
        }

        if (!out.likelyHiddenAppListModuleDetected) {
            Log.d(TAG, "no reliable hidden-app-list module found; candidates="
                    + (candidatesForBinding == null ? 0 : candidatesForBinding.size()));
            // 无法可靠识别时，如果存在多个模块，则提供“绑定”入口，做到后续必中
            out.ambiguousCandidates = candidatesForBinding;
            return out;
        }

        // 仅在“已安装”后才执行严格激活检测（避免未安装时做无意义的激活探测）。
        Set<String> strictEnabledPackages = readStrictEnabledPackagesViaSu();
        out.lsposedInstalled = isLikelyLsposedInstalledViaSu(hintedPackages, strictEnabledPackages);
        out.likelyModuleEnabled = !out.bestPackageName.isEmpty() && strictEnabledPackages.contains(out.bestPackageName);
        Log.d(TAG, "activation check after installed: pkg=" + out.bestPackageName
                + ", strictEnabledPackagesCount=" + strictEnabledPackages.size()
                + ", enabled=" + out.likelyModuleEnabled);
        return out;
    }

    private ConfigHit findHiddenAppListConfigViaSu(String packageName, String dataDirFromPm) {
        String pkg = packageName == null ? "" : packageName.trim();
        if (pkg.isEmpty()) return new ConfigHit(false, "", "");
        List<String> directCandidates = new ArrayList<>();

        // 1) 以系统返回的 dataDir 为准（最可靠）
        if (dataDirFromPm != null) {
            String dd = dataDirFromPm.trim();
            if (!dd.isEmpty()) {
                directCandidates.add(dd + "/files/config.json");
            }
        }

        // 2) 常见兜底路径
        directCandidates.add("/data/user/0/" + pkg + "/files/config.json");
        directCandidates.add("/data/user_de/0/" + pkg + "/files/config.json");
        directCandidates.add("/data/data/" + pkg + "/files/config.json");

        // 3) 多用户兜底
        List<String> userIds = listNumericDirNamesViaSu("/data/user");
        for (String id : userIds) {
            directCandidates.add("/data/user/" + id + "/" + pkg + "/files/config.json");
        }
        List<String> userDeIds = listNumericDirNamesViaSu("/data/user_de");
        for (String id : userDeIds) {
            directCandidates.add("/data/user_de/" + id + "/" + pkg + "/files/config.json");
        }

        // 去重 + 按顺序检查
        List<String> uniq = new ArrayList<>();
        for (String p : directCandidates) {
            if (p == null) continue;
            String s = p.trim();
            if (s.isEmpty()) continue;
            if (!uniq.contains(s)) uniq.add(s);
        }
        for (String path : uniq) {
            if (isFileExistsViaSu(path)) {
                return new ConfigHit(true, path, "");
            }
        }

        // 诊断：输出 dataDir + /data/user/0 + /data/data 的可见性
        String diag = buildConfigPathDiagnostic(pkg, dataDirFromPm);

        // 轻量枚举：有些模块会放在 files 的子目录里，比如 files/<name>/config.json
        List<String> filesDirs = new ArrayList<>();
        if (dataDirFromPm != null) {
            String dd = dataDirFromPm.trim();
            if (!dd.isEmpty()) filesDirs.add(dd + "/files");
        }
        filesDirs.add("/data/user/0/" + pkg + "/files");
        filesDirs.add("/data/user_de/0/" + pkg + "/files");
        filesDirs.add("/data/data/" + pkg + "/files");
        for (String id : userIds) {
            filesDirs.add("/data/user/" + id + "/" + pkg + "/files");
        }
        for (String id : userDeIds) {
            filesDirs.add("/data/user_de/" + id + "/" + pkg + "/files");
        }

        List<String> filesDirsUniq = new ArrayList<>();
        for (String d : filesDirs) {
            if (d == null) continue;
            String s = d.trim();
            if (s.isEmpty()) continue;
            if (!filesDirsUniq.contains(s)) filesDirsUniq.add(s);
        }

        for (String dir : filesDirsUniq) {
            if (!isDirectoryExistsViaSu(dir)) continue;
            // 先看一级目录
            List<String> children = listDirNamesViaSu(dir);
            if (children.contains("config.json")) {
                return new ConfigHit(true, dir + "/config.json", "");
            }
            // 再看二级：dir/<child>/config.json
            for (String child : children) {
                if (child == null || child.trim().isEmpty()) continue;
                String sub = dir + "/" + child.trim();
                if (!isDirectoryExistsViaSu(sub)) continue;
                if (isFileExistsViaSu(sub + "/config.json")) {
                    return new ConfigHit(true, sub + "/config.json", "");
                }
            }
        }

        return new ConfigHit(false, "", diag);
    }

    private String buildConfigPathDiagnostic(String pkg, String dataDirFromPm) {
        String dataDir = dataDirFromPm == null ? "" : dataDirFromPm.trim();
        String baseData = "/data/data/" + pkg;
        String baseUser0 = "/data/user/0/" + pkg;
        String dirData = baseData + "/files";
        String dirUser0 = baseUser0 + "/files";
        String fileData = dirData + "/config.json";
        String fileUser0 = dirUser0 + "/config.json";
        StringBuilder sb = new StringBuilder();
        sb.append("诊断（数据目录路径）：\n");
        if (!dataDir.isEmpty()) {
            sb.append("dataDir=").append(dataDir).append('\n');
            sb.append("ls -ld ").append(dataDir).append(" => ").append(runShellAndGetFirstLine("ls -ld \"" + dataDir + "\" 2>&1")).append('\n');
            sb.append("ls -ld ").append(dataDir).append("/files => ").append(runShellAndGetFirstLine("ls -ld \"" + dataDir + "/files\" 2>&1")).append('\n');
            sb.append("ls -l ").append(dataDir).append("/files/config.json => ").append(runShellAndGetFirstLine("ls -l \"" + dataDir + "/files/config.json\" 2>&1")).append('\n');
        }
        sb.append("ls -ld ").append(baseUser0).append(" => ").append(runShellAndGetFirstLine("ls -ld \"" + baseUser0 + "\" 2>&1")).append('\n');
        sb.append("ls -ld ").append(dirUser0).append(" => ").append(runShellAndGetFirstLine("ls -ld \"" + dirUser0 + "\" 2>&1")).append('\n');
        sb.append("ls -l ").append(fileUser0).append(" => ").append(runShellAndGetFirstLine("ls -l \"" + fileUser0 + "\" 2>&1")).append('\n');
        sb.append("ls -ld ").append(baseData).append(" => ").append(runShellAndGetFirstLine("ls -ld \"" + baseData + "\" 2>&1")).append('\n');
        sb.append("ls -ld ").append(dirData).append(" => ").append(runShellAndGetFirstLine("ls -ld \"" + dirData + "\" 2>&1")).append('\n');
        sb.append("ls -l ").append(fileData).append(" => ").append(runShellAndGetFirstLine("ls -l \"" + fileData + "\" 2>&1")).append('\n');
        return sb.toString().trim();
    }

    private List<String> listNumericDirNamesViaSu(String baseDir) {
        List<String> out = new ArrayList<>();
        String dir = baseDir == null ? "" : baseDir.trim();
        if (dir.isEmpty()) return out;
        if (!isDirectoryExistsViaSu(dir)) return out;
        List<String> names = listDirNamesViaSu(dir);
        for (String name : names) {
            if (name == null) continue;
            String s = name.trim();
            if (s.matches("\\d+")) {
                out.add(s);
            }
        }
        return out;
    }

    private String runShellAndGetFirstLine(String cmd) {
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroy();
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        return line.trim();
                    }
                }
                return "（无输出，exit=" + process.exitValue() + "）";
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return "（su 不可用）";
    }

    private List<String[]> buildSuCommands(String cmd) {
        List<String[]> out = new ArrayList<>();
        String c = cmd == null ? "" : cmd;
        // 兼容 Magisk / KernelSU 的不同 su 参数：
        // - KernelSU 常见：--mount-master 或 -M（不同版本可能不同）
        // - Magisk 常见：-mm
        out.add(new String[]{"su", "--mount-master", "-c", c});
        out.add(new String[]{"su", "-M", "-c", c});
        out.add(new String[]{"su", "-mm", "-c", c});
        out.add(new String[]{"su", "-c", c});
        out.add(new String[]{"/system/bin/su", "-c", c});
        out.add(new String[]{"/system/xbin/su", "-c", c});
        return out;
    }

    private boolean isFileExistsViaSu(String absolutePath) {
        String path = absolutePath == null ? "" : absolutePath;
        String cmd = "[ -f \"" + path + "\" ]";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(800, TimeUnit.MILLISECONDS);
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

    private List<String> listDirNamesViaSu(String absoluteDir) {
        List<String> out = new ArrayList<>();
        String dir = absoluteDir == null ? "" : absoluteDir.trim();
        if (dir.isEmpty()) return out;
        String cmd = "ls -1 \"" + dir + "\"";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (!finished || process.exitValue() != 0) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String s = line.trim();
                        if (!s.isEmpty()) out.add(s);
                    }
                }
                return out;
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return out;
    }

    private static String safeGetAppLabel(PackageManager pm, ApplicationInfo ai) {
        if (pm == null || ai == null) return "";
        try {
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? "" : label.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolveXposedDescription(PackageManager pm, ApplicationInfo ai, Bundle md) {
        if (pm == null || ai == null || md == null || !md.containsKey("xposeddescription")) {
            return "";
        }
        try {
            Object raw = md.get("xposeddescription");
            if (raw == null) return "";
            if (raw instanceof Integer) {
                int resId = (Integer) raw;
                try {
                    return pm.getResourcesForApplication(ai).getString(resId);
                } catch (Throwable ignored) {
                    return "";
                }
            }
            return String.valueOf(raw);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int scoreHiddenAppListCandidate(String label, String desc, String pkg, boolean enabled) {
        String hay = (label == null ? "" : label)
                + " "
                + (desc == null ? "" : desc)
                + " "
                + (pkg == null ? "" : pkg);
        String s = hay.toLowerCase(Locale.ROOT);

        int score = 0;
        // 强关键词
        if (s.contains("应用列表") || s.contains("app list")) score += 3;
        if (s.contains("隐藏") || s.contains("hide")) score += 2;
        // 中等关键词
        if (s.contains("cloak") || s.contains("cloack") || s.contains("rootcloak") || s.contains("root clo")) score += 2;
        if (s.contains("root") && (s.contains("hide") || s.contains("隐藏"))) score += 1;
        // 启用状态作为轻量加权（避免未启用但更匹配的模块被误压制）
        if (enabled) score += 1;

        return score;
    }

    private static String getSigningCertSha256(PackageManager pm, String packageName) {
        if (pm == null || packageName == null || packageName.trim().isEmpty()) return "";
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi == null || pi.signingInfo == null) return "";
            android.content.pm.Signature[] sigs = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners()
                    : pi.signingInfo.getSigningCertificateHistory();
            if (sigs == null || sigs.length == 0 || sigs[0] == null) return "";
            byte[] cert = sigs[0].toByteArray();
            return sha256Hex(cert);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String getXposedInitSha256(String sourceDir) {
        if (sourceDir == null || sourceDir.trim().isEmpty()) return "";
        ZipFile zip = null;
        try {
            zip = new ZipFile(sourceDir);
            ZipEntry entry = zip.getEntry("assets/xposed_init");
            if (entry == null) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (!s.isEmpty()) {
                        sb.append(s).append('\n');
                    }
                }
            }
            return sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return "";
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String sha256Hex(byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void showBindModuleDialog(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        List<Candidate> safe = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c != null && c.packageName != null && !c.packageName.trim().isEmpty()) {
                safe.add(c);
            }
        }
        if (safe.isEmpty()) return;

        CharSequence[] items = new CharSequence[safe.size()];
        for (int i = 0; i < safe.size(); i++) {
            Candidate c = safe.get(i);
            String suffix = c.enabled ? "（已启用）" : "（未启用）";
            String config = c.hasConfigJson ? "，命中特征文件" : "";
            items[i] = c.displayName + suffix + config + "\n" + c.packageName;
        }

        new AlertDialog.Builder(this)
                .setTitle("绑定隐藏应用列表模块")
                .setItems(items, (dialog, which) -> {
                    if (which < 0 || which >= safe.size()) return;
                    Candidate chosen = safe.get(which);
                    getSharedPreferences(PREFS_HIDDEN_APP_LIST, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_FINGERPRINT_DISPLAY_NAME, chosen.displayName == null ? "" : chosen.displayName)
                            .putString(KEY_FINGERPRINT_CERT_SHA256, chosen.certSha256 == null ? "" : chosen.certSha256)
                            .putString(KEY_FINGERPRINT_XPOSED_INIT_SHA256, chosen.xposedInitSha256 == null ? "" : chosen.xposedInitSha256)
                            .apply();
                    detectHiddenAppListModule();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清除绑定", (dialog, which) -> {
                    getSharedPreferences(PREFS_HIDDEN_APP_LIST, MODE_PRIVATE)
                            .edit()
                            .remove(KEY_FINGERPRINT_DISPLAY_NAME)
                            .remove(KEY_FINGERPRINT_CERT_SHA256)
                            .remove(KEY_FINGERPRINT_XPOSED_INIT_SHA256)
                            .apply();
                    detectHiddenAppListModule();
                })
                .show();
    }

    private Set<String> readEnabledLsposedModulePackagesViaSu() {
        // LSPosed 的配置格式/路径在不同版本可能不同，这里做多路径兜底解析：
        // 只要能从配置文件里提取出包名，就当作“已启用模块包名集合”使用。
        Set<String> out = new HashSet<>();
        for (String path : LSPOSED_CONFIG_FILE_CANDIDATES) {
            String content = readTextFileViaSu(path);
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            Matcher matcher = PACKAGE_NAME_PATTERN.matcher(content);
            while (matcher.find()) {
                out.add(matcher.group());
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    private boolean isLikelyLsposedInstalledViaSu(Set<String> hintedPackages, Set<String> strictEnabledPackages) {
        if (strictEnabledPackages != null && !strictEnabledPackages.isEmpty()) {
            return true;
        }
        if (hintedPackages != null && !hintedPackages.isEmpty()) {
            return true;
        }
        for (String dir : LSPOSED_MODULE_DIR_CANDIDATES) {
            if (isDirectoryExistsViaSu(dir)) {
                return true;
            }
        }
        for (String file : LSPOSED_CONFIG_FILE_CANDIDATES) {
            if (isFileExistsViaSu(file)) {
                return true;
            }
        }
        for (String file : LSPOSED_DB_CANDIDATES) {
            if (isFileExistsViaSu(file)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> readStrictEnabledPackagesViaSu() {
        Set<String> out = new HashSet<>();
        String dbPath = "/data/adb/lspd/config/modules_config.db";
        if (!isFileExistsViaSu(dbPath)) {
            Log.d(TAG, "strict db not found: " + dbPath);
            return out;
        }
        if (!isSqliteAvailableViaSu()) {
            Log.d(TAG, "sqlite3 not available via su");
            return readStrictEnabledPackagesFromCopiedDbViaSu(dbPath);
        }
        Log.d(TAG, "strict db exists, start schema probing: " + dbPath);

        List<String> tableLines = runSuCommandLines(
                "sqlite3 \"" + dbPath + "\" \"SELECT name FROM sqlite_master WHERE type='table';\"",
                2200,
                80_000
        );
        List<String> tables = new ArrayList<>();
        for (String line : tableLines) {
            if (line == null) continue;
            String name = line.trim();
            if (name.isEmpty()) continue;
            tables.add(name);
        }
        Log.d(TAG, "sqlite tables count=" + tables.size() + ", names=" + tables);
        if (tables.isEmpty()) {
            return out;
        }

        for (String table : tables) {
            String tbl = table == null ? "" : table.trim();
            if (tbl.isEmpty()) continue;
            List<String> pragma = runSuCommandLines(
                    "sqlite3 \"" + dbPath + "\" \"PRAGMA table_info('" + tbl.replace("'", "''") + "');\"",
                    2200,
                    120_000
            );
            if (pragma.isEmpty()) continue;

            List<String> packageCols = new ArrayList<>();
            List<String> enabledCols = new ArrayList<>();
            for (String row : pragma) {
                if (row == null) continue;
                String[] parts = row.split("\\|");
                if (parts.length < 2) continue;
                String col = parts[1] == null ? "" : parts[1].trim();
                if (col.isEmpty()) continue;
                String lower = col.toLowerCase(Locale.ROOT);
                if (lower.contains("package") || lower.contains("pkg")) {
                    packageCols.add(col);
                }
                if (lower.contains("enable") || lower.contains("active") || lower.contains("state")) {
                    enabledCols.add(col);
                }
            }
            Log.d(TAG, "table=" + tbl + ", packageCols=" + packageCols + ", enabledCols=" + enabledCols);
            if (packageCols.isEmpty() || enabledCols.isEmpty()) {
                continue;
            }

            String safeTable = "\"" + tbl.replace("\"", "\"\"") + "\"";
            for (String pkgCol : packageCols) {
                String safePkg = "\"" + pkgCol.replace("\"", "\"\"") + "\"";
                for (String enabledCol : enabledCols) {
                    String safeEnabled = "\"" + enabledCol.replace("\"", "\"\"") + "\"";
                    String sql = "SELECT " + safePkg + " FROM " + safeTable
                            + " WHERE " + safePkg + " IS NOT NULL AND TRIM(" + safePkg + ")!=''"
                            + " AND (LOWER(CAST(" + safeEnabled + " AS TEXT)) IN ('1','true','enabled','on')"
                            + " OR CAST(" + safeEnabled + " AS INTEGER)=1);";
                    List<String> lines = runSuCommandLines(
                            "sqlite3 \"" + dbPath + "\" \"" + sql + "\"",
                            2200,
                            120_000
                    );
                    Log.d(TAG, "query table=" + tbl + ", pkgCol=" + pkgCol
                            + ", enabledCol=" + enabledCol + ", rows=" + lines.size());
                    for (String l : lines) {
                        if (l == null) continue;
                        String s = l.trim();
                        if (s.isEmpty()) continue;
                        Matcher matcher = PACKAGE_NAME_PATTERN.matcher(s);
                        while (matcher.find()) {
                            String hitPkg = matcher.group();
                            out.add(hitPkg);
                            Log.d(TAG, "strict enabled pkg hit: " + hitPkg);
                        }
                    }
                }
            }
        }
        Log.d(TAG, "strict enabled package final count=" + out.size() + ", pkgs=" + out);
        return out;
    }

    private Set<String> readStrictEnabledPackagesFromCopiedDbViaSu(String srcDbPath) {
        Set<String> out = new HashSet<>();
        File cacheDir = getCacheDir();
        if (cacheDir == null) {
            Log.d(TAG, "cache dir unavailable for db fallback");
            return out;
        }

        File localDb = new File(cacheDir, "lspd_modules_config.db");
        File localWal = new File(cacheDir, "lspd_modules_config.db-wal");
        File localShm = new File(cacheDir, "lspd_modules_config.db-shm");
        int appUid = android.os.Process.myUid();
        try {
            if (localDb.exists()) localDb.delete();
            if (localWal.exists()) localWal.delete();
            if (localShm.exists()) localShm.delete();
        } catch (Exception ignored) {
        }

        String copyDbCmd = "cp \"" + srcDbPath + "\" \"" + localDb.getAbsolutePath() + "\""
                + " && chown " + appUid + ":" + appUid + " \"" + localDb.getAbsolutePath() + "\""
                + " && chmod 600 \"" + localDb.getAbsolutePath() + "\"";
        List<String> copyDbOut = runSuCommandLines(copyDbCmd, 2200, 2048);
        if (!localDb.exists()) {
            Log.d(TAG, "fallback copy db failed, output=" + copyDbOut);
            return out;
        }
        runSuCommandLines(
                "cp \"" + srcDbPath + "-wal\" \"" + localWal.getAbsolutePath() + "\" 2>/dev/null;"
                        + " chown " + appUid + ":" + appUid + " \"" + localWal.getAbsolutePath() + "\" 2>/dev/null;"
                        + " chmod 600 \"" + localWal.getAbsolutePath() + "\" 2>/dev/null",
                1200,
                512
        );
        runSuCommandLines(
                "cp \"" + srcDbPath + "-shm\" \"" + localShm.getAbsolutePath() + "\" 2>/dev/null;"
                        + " chown " + appUid + ":" + appUid + " \"" + localShm.getAbsolutePath() + "\" 2>/dev/null;"
                        + " chmod 600 \"" + localShm.getAbsolutePath() + "\" 2>/dev/null",
                1200,
                512
        );

        Log.d(TAG, "fallback open copied db: " + localDb.getAbsolutePath()
                + ", exists=" + localDb.exists() + ", canRead=" + localDb.canRead()
                + ", uid=" + appUid);
        SQLiteDatabase db = null;
        Cursor tableCursor = null;
        try {
            db = SQLiteDatabase.openDatabase(localDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            List<String> tables = new ArrayList<>();
            while (tableCursor.moveToNext()) {
                String name = tableCursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    tables.add(name.trim());
                }
            }
            Log.d(TAG, "fallback sqlite tables count=" + tables.size() + ", names=" + tables);

            for (String tbl : tables) {
                List<String> packageCols = new ArrayList<>();
                List<String> enabledCols = new ArrayList<>();
                Cursor pragma = null;
                try {
                    pragma = db.rawQuery("PRAGMA table_info('" + tbl.replace("'", "''") + "')", null);
                    while (pragma.moveToNext()) {
                        String col = pragma.getString(1);
                        if (col == null || col.trim().isEmpty()) continue;
                        String lower = col.toLowerCase(Locale.ROOT);
                        if (lower.contains("package") || lower.contains("pkg")) {
                            packageCols.add(col);
                        }
                        if (lower.contains("enable") || lower.contains("active") || lower.contains("state")) {
                            enabledCols.add(col);
                        }
                    }
                } finally {
                    if (pragma != null) pragma.close();
                }
                Log.d(TAG, "fallback table=" + tbl + ", packageCols=" + packageCols + ", enabledCols=" + enabledCols);
                if (packageCols.isEmpty() || enabledCols.isEmpty()) continue;

                String safeTable = "\"" + tbl.replace("\"", "\"\"") + "\"";
                for (String pkgCol : packageCols) {
                    String safePkg = "\"" + pkgCol.replace("\"", "\"\"") + "\"";
                    for (String enabledCol : enabledCols) {
                        String safeEnabled = "\"" + enabledCol.replace("\"", "\"\"") + "\"";
                        String sql = "SELECT " + safePkg + " FROM " + safeTable
                                + " WHERE " + safePkg + " IS NOT NULL AND TRIM(" + safePkg + ")!=''"
                                + " AND (LOWER(CAST(" + safeEnabled + " AS TEXT)) IN ('1','true','enabled','on')"
                                + " OR CAST(" + safeEnabled + " AS INTEGER)=1)";
                        Cursor c = null;
                        int rowCount = 0;
                        try {
                            c = db.rawQuery(sql, null);
                            while (c.moveToNext()) {
                                rowCount++;
                                String value = c.getString(0);
                                if (value == null) continue;
                                Matcher matcher = PACKAGE_NAME_PATTERN.matcher(value.trim());
                                while (matcher.find()) {
                                    String hitPkg = matcher.group();
                                    out.add(hitPkg);
                                    Log.d(TAG, "fallback strict enabled pkg hit: " + hitPkg);
                                }
                            }
                        } finally {
                            if (c != null) c.close();
                        }
                        Log.d(TAG, "fallback query table=" + tbl + ", pkgCol=" + pkgCol
                                + ", enabledCol=" + enabledCol + ", rows=" + rowCount);
                    }
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "fallback parse copied db failed: " + t.getMessage());
        } finally {
            if (tableCursor != null) tableCursor.close();
            if (db != null) db.close();
            try {
                if (localDb.exists()) localDb.delete();
                if (localWal.exists()) localWal.delete();
                if (localShm.exists()) localShm.delete();
            } catch (Exception ignored) {
            }
        }
        Log.d(TAG, "fallback strict enabled package final count=" + out.size() + ", pkgs=" + out);
        return out;
    }

    private boolean isSqliteAvailableViaSu() {
        List<String> lines = runSuCommandLines("command -v sqlite3", 1200, 512);
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<String> runSuCommandLines(String cmd, long timeoutMs, int maxChars) {
        List<String> out = new ArrayList<>();
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished || process.exitValue() != 0) {
                    continue;
                }
                int total = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.add(line);
                        total += line.length() + 1;
                        if (total >= maxChars) {
                            break;
                        }
                    }
                }
                return out;
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return out;
    }

    private String readTextFileViaSu(String absolutePath) {
        String path = absolutePath == null ? "" : absolutePath;
        String cmd = "cat \"" + path + "\"";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroy();
                    continue;
                }
                if (process.exitValue() != 0) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                return sb.toString().trim();
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return null;
    }

    private void updateModuleUi(boolean installed) {
        if (installed) {
            cardTrickyStoreModule.setVisibility(View.VISIBLE);
            cardTrickyStoreModule.setOnClickListener(v ->
                    startActivity(new Intent(this, TrickyStoreAppListActivity.class)));
            return;
        }
        cardTrickyStoreModule.setVisibility(View.GONE);
        cardTrickyStoreModule.setOnClickListener(null);
    }

    private static class DetectionResult {
        boolean anyXposedModuleDetected = false;
        int xposedModuleCount = 0;
        boolean likelyHiddenAppListModuleDetected = false;
        boolean lsposedInstalled = false;
        boolean likelyModuleEnabled = false;
        String displayName = "未知模块";
        String bestPackageName = "";
        int bestScore = 0;
        List<Candidate> ambiguousCandidates = null;
        boolean matchedByConfigJson = false;
        String configJsonPath = "";
        String configJsonDiagnostic = "";
    }

    private static class Candidate {
        String packageName = "";
        String displayName = "";
        int score = 0;
        boolean enabled = false;
        String certSha256 = "";
        String xposedInitSha256 = "";
        boolean hasConfigJson = false;
        String configJsonPath = "";
        String configJsonDiagnostic = "";
    }

    private static class ConfigHit {
        final boolean hit;
        final String path;
        final String diagnostic;

        ConfigHit(boolean hit, String path, String diagnostic) {
            this.hit = hit;
            this.path = path == null ? "" : path;
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    private boolean isDirectoryExistsViaSu(String directoryPath) {
        String path = directoryPath == null ? "" : directoryPath;
        String cmd = "[ -d \"" + path + "\" ]";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
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

}
