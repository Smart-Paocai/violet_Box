package com.violet.safe.ui.plugin;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KernelDisguiseActivity extends AppCompatActivity {

    private static final String SUSFS_CONFIG_PRIMARY = "/data/adb/violet_kernel_spoof/config.sh";
    private static final String SUSFS_CONFIG_LEGACY = "/data/adb/susfs4ksu/config.sh";
    private static final String SUSFS_BIN = "/data/adb/ksu/bin/ksu_susfs";
    private static final Pattern CONFIG_LINE_PATTERN = Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=\\s*(.*)\\s*$");

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView tvCurrentKernelVersion;
    private TextView tvCurrentKernelBuild;
    private TextView tvSpoofedKernelVersion;
    private TextView tvSpoofedKernelBuild;
    private TextInputEditText etKernelVersion;
    private TextInputEditText etKernelBuild;
    private RadioGroup rgSpoofUnameMode;
    private MaterialButton btnSyncCurrent;
    private MaterialButton btnApplySpoof;
    private View progress;
    private volatile String lastApplyError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kernel_disguise);

        Toolbar toolbar = findViewById(R.id.toolbarKernelDisguise);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("内核伪装");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCurrentKernelVersion = findViewById(R.id.tvCurrentKernelVersion);
        tvCurrentKernelBuild = findViewById(R.id.tvCurrentKernelBuild);
        tvSpoofedKernelVersion = findViewById(R.id.tvSpoofedKernelVersion);
        tvSpoofedKernelBuild = findViewById(R.id.tvSpoofedKernelBuild);
        etKernelVersion = findViewById(R.id.etKernelVersion);
        etKernelBuild = findViewById(R.id.etKernelBuild);
        rgSpoofUnameMode = findViewById(R.id.rgSpoofUnameMode);
        btnSyncCurrent = findViewById(R.id.btnKernelSyncCurrent);
        btnApplySpoof = findViewById(R.id.btnKernelApplySpoof);
        progress = findViewById(R.id.progressKernelDisguise);

        btnSyncCurrent.setOnClickListener(v -> syncInputWithCurrentKernel());
        btnApplySpoof.setOnClickListener(v -> applySpoofKernelInfo());

        loadKernelInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (btnSyncCurrent != null) btnSyncCurrent.setEnabled(!busy);
        if (btnApplySpoof != null) btnApplySpoof.setEnabled(!busy);
        if (etKernelVersion != null) etKernelVersion.setEnabled(!busy);
        if (etKernelBuild != null) etKernelBuild.setEnabled(!busy);
        if (rgSpoofUnameMode != null) rgSpoofUnameMode.setEnabled(!busy);
    }

    private void loadKernelInfo() {
        setBusy(true);
        ioExecutor.execute(() -> {
            String version = trimOrEmpty(runShellFirstLineViaSu("uname -r"));
            String build = trimOrEmpty(runShellFirstLineViaSu("uname -v"));
            String config = readTextFileViaSu(resolveConfigPathForRead());
            String spoofVersion = parseConfigValue(config, "kernel_version");
            String spoofBuild = parseConfigValue(config, "kernel_build");
            String spoofMode = parseConfigValue(config, "spoof_uname");
            runOnUiThread(() -> {
                setBusy(false);
                tvCurrentKernelVersion.setText("当前内核版本：" + valueOrDash(version));
                tvCurrentKernelBuild.setText("当前构建信息：" + valueOrDash(build));
                tvSpoofedKernelVersion.setText("伪装内核版本：" + valueOrDash(spoofVersion));
                tvSpoofedKernelBuild.setText("伪装构建日期：" + valueOrDash(spoofBuild));
                if (etKernelVersion != null) etKernelVersion.setText("");
                if (etKernelBuild != null) etKernelBuild.setText("");
                applySpoofModeToUi(spoofMode);
                Toast.makeText(this, "已读取内核信息", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void syncInputWithCurrentKernel() {
        String version = extractValueAfterPrefix(tvCurrentKernelVersion, "当前内核版本：");
        String build = extractValueAfterPrefix(tvCurrentKernelBuild, "当前构建信息：");
        if (etKernelVersion != null) etKernelVersion.setText("—".equals(version) ? "" : version);
        if (etKernelBuild != null) etKernelBuild.setText("—".equals(build) ? "" : build);
        Toast.makeText(this, "已同步为当前内核信息", Toast.LENGTH_SHORT).show();
    }

    private void applySpoofKernelInfo() {
        final String kernelVersion = normalizeInput(etKernelVersion);
        final String kernelBuild = normalizeInput(etKernelBuild);
        if (kernelVersion.isEmpty() || kernelBuild.isEmpty()) {
            Toast.makeText(this, "内核版本与构建日期不为空", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        ioExecutor.execute(() -> {
            boolean ok = writeSpoofConfigAndApply(kernelVersion, kernelBuild);
            runOnUiThread(() -> {
                setBusy(false);
                if (ok) {
                    Toast.makeText(this, "伪装设置已应用", Toast.LENGTH_SHORT).show();
                } else {
                    String detail = trimOrEmpty(lastApplyError);
                    if (detail.length() > 80) detail = detail.substring(0, 80) + "...";
                    String msg = detail.isEmpty()
                            ? "应用失败，请检查配置环境"
                            : "应用失败：" + detail;
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                if (ok) loadKernelInfo();
            });
        });
    }

    private boolean writeSpoofConfigAndApply(String kernelVersion, String kernelBuild) {
        String versionEsc = singleQuoteEscape(kernelVersion);
        String buildEsc = singleQuoteEscape(kernelBuild);
        String spoofMode = getSelectedSpoofMode();
        String cmd = "PRIMARY=\"" + SUSFS_CONFIG_PRIMARY + "\"; LEGACY=\"" + SUSFS_CONFIG_LEGACY + "\"; "
                + "if [ -f \"$PRIMARY\" ] || [ -d /data/adb/violet_kernel_spoof ]; then CONFIG=\"$PRIMARY\"; "
                + "else CONFIG=\"$LEGACY\"; fi; "
                + "KV='" + versionEsc + "'; KB='" + buildEsc + "'; "
                + "mkdir -p \"$(dirname \"$CONFIG\")\"; "
                + "[ -f \"$CONFIG\" ] || touch \"$CONFIG\"; "
                + "if grep -q '^spoof_uname=' \"$CONFIG\"; then "
                + "sed -i \"s|^spoof_uname=.*|spoof_uname=" + spoofMode + "|\" \"$CONFIG\"; "
                + "else echo \"spoof_uname=" + spoofMode + "\" >> \"$CONFIG\"; fi; "
                + "if grep -q '^kernel_version=' \"$CONFIG\"; then "
                + "sed -i \"s|^kernel_version=.*|kernel_version='$KV'|\" \"$CONFIG\"; "
                + "else echo \"kernel_version='$KV'\" >> \"$CONFIG\"; fi; "
                + "if grep -q '^kernel_build=' \"$CONFIG\"; then "
                + "sed -i \"s|^kernel_build=.*|kernel_build='$KB'|\" \"$CONFIG\"; "
                + "else echo \"kernel_build='$KB'\" >> \"$CONFIG\"; fi; "
                + "if [ -x \"" + SUSFS_BIN + "\" ]; then \"" + SUSFS_BIN + "\" set_uname \"$KV\" \"$KB\"; "
                + "else ksu_susfs set_uname \"$KV\" \"$KB\"; fi";
        ShellExecResult result = runShellViaSuDetailed(cmd, 7000);
        lastApplyError = result.summary;
        return result.success;
    }

    private String resolveConfigPathForRead() {
        if (isFileExistsViaSu(SUSFS_CONFIG_PRIMARY) || isDirectoryExistsViaSu("/data/adb/violet_kernel_spoof")) {
            return SUSFS_CONFIG_PRIMARY;
        }
        return SUSFS_CONFIG_LEGACY;
    }

    private String runShellFirstLineViaSu(String cmd) {
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd).redirectErrorStream(true).start();
                boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
                if (!finished || process.exitValue() != 0) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return line == null ? "" : line.trim();
                }
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return "";
    }

    private boolean runShellViaSu(String cmd, long timeoutMs) {
        return runShellViaSuDetailed(cmd, timeoutMs).success;
    }

    private ShellExecResult runShellViaSuDetailed(String cmd, long timeoutMs) {
        List<String> failures = new ArrayList<>();
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd).redirectErrorStream(true).start();
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (finished && process.exitValue() == 0) {
                    return new ShellExecResult(true, "");
                }
                String first = readFirstLine(process);
                String reason = finished ? ("exit=" + process.exitValue()) : "timeout";
                if (!first.isEmpty()) reason += " " + first;
                failures.add(reason);
            } catch (Exception ignored) {
                failures.add("exec error");
            } finally {
                if (process != null) process.destroy();
            }
        }
        return new ShellExecResult(false, failures.isEmpty() ? "unknown error" : failures.get(0));
    }

    private String readTextFileViaSu(String absolutePath) {
        String cmd = "cat \"" + absolutePath + "\"";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd).redirectErrorStream(true).start();
                boolean finished = process.waitFor(1600, TimeUnit.MILLISECONDS);
                if (!finished || process.exitValue() != 0) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                return sb.toString();
            } catch (Exception ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return null;
    }

    private boolean isFileExistsViaSu(String absolutePath) {
        String cmd = "[ -f \"" + absolutePath + "\" ]";
        return runShellViaSu(cmd, 1200);
    }

    private boolean isDirectoryExistsViaSu(String absolutePath) {
        String cmd = "[ -d \"" + absolutePath + "\" ]";
        return runShellViaSu(cmd, 1200);
    }

    private static String parseConfigValue(String rawConfig, String key) {
        if (rawConfig == null || rawConfig.trim().isEmpty() || key == null || key.trim().isEmpty()) {
            return "";
        }
        String[] lines = rawConfig.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            Matcher m = CONFIG_LINE_PATTERN.matcher(s);
            if (!m.matches()) continue;
            String k = m.group(1).trim();
            if (!key.equals(k)) continue;
            String v = m.group(2).trim();
            if ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\""))) {
                v = v.substring(1, v.length() - 1);
            }
            return v.trim();
        }
        return "";
    }

    private void applySpoofModeToUi(String spoofMode) {
        if (rgSpoofUnameMode == null) return;
        String mode = trimOrEmpty(spoofMode);
        if ("0".equals(mode)) {
            rgSpoofUnameMode.check(R.id.rbSpoofUnameOff);
            return;
        }
        if ("2".equals(mode)) {
            rgSpoofUnameMode.check(R.id.rbSpoofUnamePostFs);
            return;
        }
        rgSpoofUnameMode.check(R.id.rbSpoofUnameService);
    }

    private String getSelectedSpoofMode() {
        if (rgSpoofUnameMode == null) return "1";
        int checkedId = rgSpoofUnameMode.getCheckedRadioButtonId();
        if (checkedId == R.id.rbSpoofUnameOff) return "0";
        if (checkedId == R.id.rbSpoofUnamePostFs) return "2";
        return "1";
    }

    private String[][] buildSuCommands(String cmd) {
        String c = cmd == null ? "" : cmd;
        return new String[][]{
                {"su", "--mount-master", "-c", c},
                {"su", "-M", "-c", c},
                {"su", "-mm", "-c", c},
                {"su", "-c", c},
                {"/system/bin/su", "-c", c},
                {"/system/xbin/su", "-c", c}
        };
    }

    private static String normalizeInput(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private static String extractValueAfterPrefix(TextView tv, String prefix) {
        if (tv == null || tv.getText() == null) return "";
        String text = tv.getText().toString();
        if (text.startsWith(prefix)) {
            return text.substring(prefix.length()).trim();
        }
        return text.trim();
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String valueOrDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    private static String singleQuoteEscape(String src) {
        if (src == null) return "";
        return src.replace("'", "'\"'\"'");
    }

    private String readFirstLine(Process process) {
        if (process == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static class ShellExecResult {
        final boolean success;
        final String summary;

        ShellExecResult(boolean success, String summary) {
            this.success = success;
            this.summary = summary == null ? "" : summary;
        }
    }
}
