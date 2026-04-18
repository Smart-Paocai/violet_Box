package com.violet.safe;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleManagerActivity extends AppCompatActivity {

    private enum TargetEngine {
        MAGISK,
        KERNEL_SU,
        APATCH
    }

    private enum RootExecMode {
        SU_C,
        SU_S_SH
    }

    private RadioGroup rgModuleTarget;
    private TextView tvModuleSummary;
    private TextView tvModuleLog;
    private TextView tvModuleProgress;
    private ProgressBar progressModuleInstall;
    private MaterialButton btnPickModules;
    private MaterialButton btnInstallModules;
    private MaterialButton btnRebootNow;
    private ActivityResultLauncher<Intent> pickModulesLauncher;
    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module_manager);

        Toolbar toolbar = findViewById(R.id.toolbarModuleManager);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("模块管理");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rgModuleTarget = findViewById(R.id.rgModuleTarget);
        tvModuleSummary = findViewById(R.id.tvModuleSummary);
        tvModuleLog = findViewById(R.id.tvModuleLog);
        tvModuleProgress = findViewById(R.id.tvModuleProgress);
        progressModuleInstall = findViewById(R.id.progressModuleInstall);
        btnPickModules = findViewById(R.id.btnPickModules);
        btnInstallModules = findViewById(R.id.btnInstallModules);
        btnRebootNow = findViewById(R.id.btnRebootNow);

        tvModuleLog.setMovementMethod(new ScrollingMovementMethod());
        tvModuleLog.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        updateSelectionSummary();

        pickModulesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    consumePickedUris(result.getData());
                });

        btnPickModules.setOnClickListener(v -> pickModules());
        btnInstallModules.setOnClickListener(v -> startBatchInstall());
        btnRebootNow.setOnClickListener(v -> confirmRebootNow());
    }

    private void pickModules() {
        if (busy) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickModulesLauncher.launch(intent);
    }

    private void consumePickedUris(Intent data) {
        final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        ArrayList<Uri> incoming = new ArrayList<>();
        Uri single = data.getData();
        if (single != null) {
            incoming.add(single);
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    incoming.add(uri);
                }
            }
        }
        if (incoming.isEmpty()) {
            Toast.makeText(this, "未选择模块文件", Toast.LENGTH_SHORT).show();
            return;
        }

        int added = 0;
        for (Uri uri : incoming) {
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException ignored) {
            }
            if (!selectedUris.contains(uri)) {
                selectedUris.add(uri);
                added++;
            }
        }
        appendLog(String.format(Locale.US, "已新增 %d 个模块（当前总数 %d）", added, selectedUris.size()));
        updateSelectionSummary();
    }

    private void startBatchInstall() {
        if (busy) {
            return;
        }
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "请先选择至少 1 个模块", Toast.LENGTH_SHORT).show();
            return;
        }
        TargetEngine engine = getSelectedEngine();
        btnRebootNow.setVisibility(View.GONE);
        setBusy(true);
        appendLog("开始刷入，目标平台：" + engineLabel(engine));

        new Thread(() -> {
            File stagingDir = new File(getCacheDir(), "module_staging_" + System.currentTimeMillis());
            //noinspection ResultOfMethodCallIgnored
            stagingDir.mkdirs();

            int total = selectedUris.size();
            int success = 0;
            int fail = 0;
            List<String> failedNames = new ArrayList<>();

            try {
                for (int i = 0; i < total; i++) {
                    Uri uri = selectedUris.get(i);
                    String name = safeZipName(queryDisplayName(uri), i);
                    int step = i + 1;
                    appendLog("[" + step + "/" + total + "] 准备安装：" + name);

                    File localZip = new File(stagingDir, name);
                    if (!copyUriToFile(uri, localZip)) {
                        fail++;
                        failedNames.add(name + "（复制失败）");
                        appendLog("  - 失败：无法读取 zip");
                        updateProgress((step * 100) / total);
                        continue;
                    }

                    ShellResult installResult = installByEngine(engine, localZip);
                    if (installResult.success) {
                        success++;
                        appendLog("  - 成功");
                    } else {
                        fail++;
                        failedNames.add(name);
                        String reason = (installResult.stderr == null || installResult.stderr.trim().isEmpty())
                                ? installResult.stdout
                                : installResult.stderr;
                        appendLog("  - 失败：" + trimForLog(reason));
                    }
                    updateProgress((step * 100) / total);
                }
            } finally {
                wipeDirectory(stagingDir);
            }

            int finalSuccess = success;
            int finalFail = fail;
            runOnUiThread(() -> {
                setBusy(false);
                appendLog("完成：成功 " + finalSuccess + "，失败 " + finalFail);
                if (!failedNames.isEmpty()) {
                    appendLog("失败列表：" + failedNames);
                }
                if (finalFail == 0) {
                    Toast.makeText(this, "全部模块刷入完成，重启设备后生效", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "部分模块刷入失败，请查看日志", Toast.LENGTH_LONG).show();
                }
                btnRebootNow.setVisibility(View.VISIBLE);
                showRebootOptionDialog();
            });
        }).start();
    }

    private TargetEngine getSelectedEngine() {
        int checkedId = rgModuleTarget.getCheckedRadioButtonId();
        if (checkedId == R.id.rbKsu) {
            return TargetEngine.KERNEL_SU;
        }
        if (checkedId == R.id.rbApatch) {
            return TargetEngine.APATCH;
        }
        return TargetEngine.MAGISK;
    }

    private String engineLabel(TargetEngine engine) {
        switch (engine) {
            case KERNEL_SU:
                return "KernelSU";
            case APATCH:
                return "APatch";
            case MAGISK:
            default:
                return "Magisk";
        }
    }

    private ShellResult installByEngine(TargetEngine engine, File moduleZip) {
        String zipPath = shellEscape(moduleZip.getAbsolutePath());
        List<String> candidates = new ArrayList<>();
        switch (engine) {
            case KERNEL_SU:
                candidates.add("ksud module install " + zipPath);
                candidates.add("/data/adb/ksud module install " + zipPath);
                break;
            case APATCH:
                candidates.add("apd module install " + zipPath);
                candidates.add("/data/adb/ap/bin/apd module install " + zipPath);
                break;
            case MAGISK:
            default:
                candidates.add("magisk --install-module " + zipPath);
                candidates.add("/data/adb/magisk/magisk --install-module " + zipPath);
                break;
        }

        ShellResult last = new ShellResult(false, "", "无法安装（-1）");
        for (String cmd : candidates) {
            ShellResult current;
            if (engine == TargetEngine.MAGISK) {
                current = runMagiskCommandWithBothSuModes(cmd);
            } else {
                appendLog("  - [su -c] 执行：" + cmd);
                current = runRootCommandStreaming(cmd, RootExecMode.SU_C, "    ");
            }
            if (current.success) {
                return current;
            }
            last = current;
        }
        return last;
    }

    private ShellResult runMagiskCommandWithBothSuModes(String cmd) {
        appendLog("  - [su -c] 执行：" + cmd);
        ShellResult first = runRootCommandStreaming(cmd, RootExecMode.SU_C, "    ");
        if (first.success) {
            return first;
        }
        appendLog("  - [su -s sh -c] 执行：" + cmd);
        ShellResult second = runRootCommandStreaming(cmd, RootExecMode.SU_S_SH, "    ");
        return second;
    }

    private void setBusy(boolean value) {
        busy = value;
        progressModuleInstall.setVisibility(value ? View.VISIBLE : View.GONE);
        tvModuleProgress.setVisibility(value ? View.VISIBLE : View.GONE);
        if (value) {
            progressModuleInstall.setProgress(0);
            tvModuleProgress.setText("进度：0%");
        }
        rgModuleTarget.setEnabled(!value);
        btnPickModules.setEnabled(!value);
        btnInstallModules.setEnabled(!value);
        btnRebootNow.setEnabled(!value);
    }

    private void updateProgress(int progress) {
        runOnUiThread(() -> {
            int safe = Math.max(0, Math.min(progress, 100));
            progressModuleInstall.setProgress(safe);
            tvModuleProgress.setText("进度：" + safe + "%");
        });
    }

    private void updateSelectionSummary() {
        tvModuleSummary.setText("已选择 " + selectedUris.size() + " 个模块");
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            CharSequence current = tvModuleLog.getText();
            if (current == null || current.length() == 0) {
                tvModuleLog.setText(line);
            } else {
                tvModuleLog.append("\n" + line);
            }
            scrollLogToBottom();
        });
    }

    private void scrollLogToBottom() {
        tvModuleLog.post(() -> {
            if (tvModuleLog.getLayout() == null) {
                return;
            }
            int scrollAmount = tvModuleLog.getLayout().getLineTop(tvModuleLog.getLineCount()) - tvModuleLog.getHeight();
            if (scrollAmount > 0) {
                tvModuleLog.scrollTo(0, scrollAmount);
            } else {
                tvModuleLog.scrollTo(0, 0);
            }
        });
    }

    private void showRebootOptionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("模块安装完成")
                .setMessage("模块需重启后生效")
                .setNegativeButton("取消", null)
                .setPositiveButton("立即重启", (dialog, which) -> rebootNow())
                .show();
    }

    private void confirmRebootNow() {
        new AlertDialog.Builder(this)
                .setTitle("确认重启")
                .setMessage("将重启你的设备")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启", (dialog, which) -> rebootNow())
                .show();
    }

    private void rebootNow() {
        appendLog("执行重启：reboot");
        new Thread(() -> {
            ShellResult result = runRootCommandStreaming("reboot", RootExecMode.SU_C, "    ");
            if (!result.success) {
                runOnUiThread(() ->
                        Toast.makeText(this, "重启命令执行失败，请手动重启", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null || last.trim().isEmpty() ? "module.zip" : last.trim();
    }

    private String safeZipName(String name, int index) {
        String base = name == null ? "" : name.replace('/', '_').replace('\\', '_').trim();
        if (base.isEmpty()) {
            base = "module_" + (index + 1) + ".zip";
        }
        if (!base.toLowerCase(Locale.US).endsWith(".zip")) {
            base = base + ".zip";
        }
        return base;
    }

    private boolean copyUriToFile(Uri src, File dst) {
        ContentResolver resolver = getContentResolver();
        try (InputStream is = resolver.openInputStream(src);
             FileOutputStream os = new FileOutputStream(dst)) {
            if (is == null) {
                return false;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void wipeDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    private static String trimForLog(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').trim();
        if (oneLine.length() > 120) {
            return oneLine.substring(0, 120) + "...";
        }
        return oneLine;
    }

    private static String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private ShellResult runRootCommandStreaming(String command, RootExecMode mode, String logPrefix) {
        Process process = null;
        try {
            if (mode == RootExecMode.SU_S_SH) {
                process = new ProcessBuilder("su", "-s", "sh", "-c", command).start();
            } else {
                process = new ProcessBuilder("su", "-c", command).start();
            }
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outThread = streamToLog(process.getInputStream(), stdout, logPrefix);
            Thread errThread = streamToLog(process.getErrorStream(), stderr, logPrefix + "[err] ");
            int exit = process.waitFor();
            outThread.join();
            errThread.join();
            return new ShellResult(exit == 0, stdout.toString(), stderr.toString());
        } catch (Exception e) {
            return new ShellResult(false, "", e.getMessage() == null ? "" : e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private Thread streamToLog(InputStream stream, StringBuilder sink, String prefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append('\n');
                    appendLog(prefix + line);
                }
            } catch (Exception ignored) {
            }
        });
        t.start();
        return t;
    }

    private static class ShellResult {
        final boolean success;
        final String stdout;
        final String stderr;

        ShellResult(boolean success, String stdout, String stderr) {
            this.success = success;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
