package com.violet.safe.ui.plugin;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

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
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VioletPluginActivity extends AppCompatActivity {

    private static final String TRICKY_STORE_DIR = "/data/adb/tricky_store";
    private static final String LSPOSED_DIR = "/data/adb/modules/lsposed";
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+");
    private static final String PREFS_HIDDEN_APP_LIST = "hidden_app_list_detector";
    private static final String KEY_FINGERPRINT_CERT_SHA256 = "fingerprint_cert_sha256";
    private static final String KEY_FINGERPRINT_XPOSED_INIT_SHA256 = "fingerprint_xposed_init_sha256";
    private static final String KEY_FINGERPRINT_DISPLAY_NAME = "fingerprint_display_name";
    // 插件页的多个模块检测需要并发执行，避免某个 su/IO 检测拖慢其它模块的加载。
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3);
    private View cardTrickyStoreModule;
    private View cardHiddenAppList;
    private TextView tvHiddenAppListMeta;
    private TextView tvHiddenAppListDetail;
    private ImageView ivHiddenAppListLoading;
    private ObjectAnimator hiddenAppListLoadingAnimator;

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
        tvHiddenAppListDetail = findViewById(R.id.tvHiddenAppListDetail);
        ivHiddenAppListLoading = findViewById(R.id.ivHiddenAppListLoading);
        if (cardHiddenAppList != null) {
            cardHiddenAppList.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_OPEN_TAB, 2);
                startActivity(intent);
            });
        }
        detectHiddenAppListModule();

        cardTrickyStoreModule = findViewById(R.id.cardTrickyStoreModule);
        detectTrickyStoreModule();
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
        if (tvHiddenAppListDetail != null) {
            tvHiddenAppListDetail.setText("查看 Root 隐藏/伪装相关检测与信息");
        }
        ioExecutor.execute(() -> {
            DetectionResult result = detectLikelyHiddenAppListXposedModule();
            runOnUiThread(() -> {
                if (tvHiddenAppListMeta == null) return;
                stopHiddenAppListLoading();
                boolean installed = result.likelyHiddenAppListModuleDetected;
                applyInstallStatusPill(tvHiddenAppListMeta, installed);
                tvHiddenAppListMeta.setOnClickListener(null);
            });
        });
    }

    private void applyInstallStatusPill(TextView target, boolean installed) {
        if (target == null) return;
        target.setText(installed ? "状态：已安装" : "状态：未安装");
        int color = ContextCompat.getColor(
                this,
                installed ? R.color.ios_semantic_positive : R.color.ios_semantic_negative
        );
        target.setTextColor(color);
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
        out.lsposedInstalled = isDirectoryExistsViaSu(LSPOSED_DIR);

        Set<String> enabledPackages = out.lsposedInstalled ? readEnabledLsposedModulePackagesViaSu() : new HashSet<>();

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
            boolean enabled = !enabledPackages.isEmpty() && !pkg.isEmpty() && enabledPackages.contains(pkg);

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
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = configMatched.displayName;
            out.likelyModuleEnabled = configMatched.enabled;
            out.bestPackageName = configMatched.packageName;
            out.bestScore = 1000;
            out.matchedByConfigJson = true;
            return out;
        }
        if (pinned != null) {
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = pinned.displayName;
            out.likelyModuleEnabled = pinned.enabled;
            out.bestPackageName = pinned.packageName;
            out.bestScore = 999;
            return out;
        }

        if (best != null && best.score >= 3) {
            out.likelyHiddenAppListModuleDetected = true;
            out.displayName = best.displayName;
            out.likelyModuleEnabled = best.enabled;
            out.bestPackageName = best.packageName;
            out.bestScore = best.score;
        } else {
            // 无法可靠识别时，如果存在多个模块，则提供“绑定”入口，做到后续必中
            out.ambiguousCandidates = candidatesForBinding;
        }
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
        String[] candidates = new String[]{
                "/data/adb/lspd/config/modules.list",
                "/data/adb/lspd/config/modules.json",
                "/data/adb/lspd/config/modules.xml",
                "/data/adb/lspd/config/modules_config.xml",
                "/data/adb/lspd/config/modules.conf",
                "/data/adb/lspd/config/modules",
                "/data/adb/lspd/config/config.json",
                "/data/adb/lspd/config/config.xml"
        };

        for (String path : candidates) {
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
    }

    private static class Candidate {
        String packageName = "";
        String displayName = "";
        int score = 0;
        boolean enabled = false;
        String certSha256 = "";
        String xposedInitSha256 = "";
        boolean hasConfigJson = false;
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
