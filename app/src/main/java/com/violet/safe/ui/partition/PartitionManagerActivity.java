package com.violet.safe.ui.partition;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Method;

public class PartitionManagerActivity extends AppCompatActivity {

    private LinearLayout layoutPartitionButtons;
    private View layoutPartitionLoading;
    private View layoutPartitionContent;
    private ProgressBar progressPartitionLoading;
    private TextView tvPartitionLoading;
    private TextView tvPartitionHint;
    private TextView tvPartitionInfo;
    private TextView tvPartitionOpStatus;

    private final Map<String, String> partitionPathMap = new HashMap<>();
    private String selectedPartition;
    private boolean isBusy = false;

    private ActivityResultLauncher<Intent> exportPartitionLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_partition_manager);

        Toolbar toolbar = findViewById(R.id.toolbarPartition);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("分区管理");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutPartitionButtons = findViewById(R.id.layoutPartitionButtons);
        layoutPartitionLoading = findViewById(R.id.layoutPartitionLoading);
        layoutPartitionContent = findViewById(R.id.layoutPartitionContent);
        progressPartitionLoading = findViewById(R.id.progressPartitionLoading);
        tvPartitionLoading = findViewById(R.id.tvPartitionLoading);
        tvPartitionHint = findViewById(R.id.tvPartitionHint);
        tvPartitionInfo = findViewById(R.id.tvPartitionInfo);
        tvPartitionOpStatus = findViewById(R.id.tvPartitionOpStatus);
        updateTopHint();

        registerActivityLaunchers();
        showLoadingState(true, "刷新分区表...");
        refreshPartitionList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 进入页面时兜底自动刷新，确保分区按钮列表一定可见
        if (!isBusy && layoutPartitionButtons.getChildCount() == 0) {
            refreshPartitionList();
        }
    }

    private void registerActivityLaunchers() {
        exportPartitionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        extractPartitionToUri(uri);
                    }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        flashPartitionFromUri(uri);
                    }
                });
    }

    private void refreshPartitionList() {
        if (isBusy) {
            return;
        }
        showLoadingState(true, "正在刷新分区表...");
        setBusy(true, "状态：正在读取分区列表...");
        new Thread(() -> {
            List<String> names = new ArrayList<>();
            Map<String, String> newPathMap = new HashMap<>();
            ShellResult listResult = runSuCommand("ls -1 /dev/block/by-name 2>/dev/null");
            if (listResult.success) {
                String[] lines = listResult.stdout.split("\n");
                for (String line : lines) {
                    String name = line.trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    names.add(name);
                    ShellResult pathResult = runSuCommand("readlink -f /dev/block/by-name/" + shellEscape(name));
                    String path = pathResult.success ? pathResult.stdout.trim() : "";
                    if (path.isEmpty()) {
                        path = "/dev/block/by-name/" + name;
                    }
                    newPathMap.put(name, path);
                }
            }
            Collections.sort(names);

            runOnUiThread(() -> {
                setBusy(false, null);
                if (names.isEmpty()) {
                    showLoadingState(false, null);
                    tvPartitionOpStatus.setText("状态：读取分区失败，未授予ROOT");
                    tvPartitionInfo.setText("分区信息: 无");
                    Toast.makeText(this, "未读取到分区列表", Toast.LENGTH_SHORT).show();
                    return;
                }
                partitionPathMap.clear();
                partitionPathMap.putAll(newPathMap);
                renderPartitionButtons(names);

                if (selectedPartition == null || !partitionPathMap.containsKey(selectedPartition)) {
                    selectedPartition = names.get(0);
                }
                highlightSelectedPartitionButton();
                updatePartitionInfo();
                tvPartitionOpStatus.setText("状态：已加载 " + names.size() + " 个分区");
                showLoadingState(false, null);
            });
        }).start();
    }

    private void renderPartitionButtons(List<String> names) {
        layoutPartitionButtons.removeAllViews();
        for (int i = 0; i < names.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i > 0) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = dpToPx(8);
            }

            MaterialButton left = buildPartitionButton(names.get(i), true);
            row.addView(left);

            if (i + 1 < names.size()) {
                MaterialButton right = buildPartitionButton(names.get(i + 1), false);
                row.addView(right);
            } else {
                View spacer = new View(this);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                spacerLp.setMarginStart(dpToPx(6));
                spacer.setLayoutParams(spacerLp);
                row.addView(spacer);
            }

            layoutPartitionButtons.addView(row);
        }
    }

    private MaterialButton buildPartitionButton(String partitionName, boolean isLeft) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dpToPx(42), 1f);
        if (isLeft) {
            lp.setMarginEnd(dpToPx(6));
        } else {
            lp.setMarginStart(dpToPx(6));
        }
        button.setLayoutParams(lp);
        button.setAllCaps(false);
        button.setText(partitionName);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTag(partitionName);
        button.setCornerRadius(dpToPx(12));
        button.setOnClickListener(v -> {
            selectedPartition = partitionName;
            highlightSelectedPartitionButton();
            updatePartitionInfo();
        });
        button.setOnLongClickListener(v -> {
            selectedPartition = partitionName;
            highlightSelectedPartitionButton();
            updatePartitionInfo();
            showPartitionActionsDialog();
            return true;
        });
        return button;
    }

    private void showPartitionActionsDialog() {
        if (!ensurePartitionSelected()) {
            return;
        }
        String[] actions = {"提取分区", "写入分区", "擦除分区"};
        new AlertDialog.Builder(this)
                .setTitle("分区操作 - " + selectedPartition)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startExportPartition();
                    } else if (which == 1) {
                        startPickImageForFlash();
                    } else if (which == 2) {
                        confirmWipePartition();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void highlightSelectedPartitionButton() {
        int selectedBg = getColor(R.color.purple_200);
        int selectedText = getColor(R.color.black);
        int defaultBg = getColor(R.color.ios_background_elevated);
        int defaultText = getColor(R.color.ios_text_primary);
        int stroke = getColor(R.color.ios_separator);

        for (int i = 0; i < layoutPartitionButtons.getChildCount(); i++) {
            View row = layoutPartitionButtons.getChildAt(i);
            if (!(row instanceof LinearLayout)) {
                continue;
            }
            LinearLayout rowLayout = (LinearLayout) row;
            for (int j = 0; j < rowLayout.getChildCount(); j++) {
                View child = rowLayout.getChildAt(j);
                if (!(child instanceof MaterialButton)) {
                    continue;
                }
                MaterialButton button = (MaterialButton) child;
                String name = String.valueOf(button.getTag());
                boolean isSelected = name.equals(selectedPartition);
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isSelected ? selectedBg : defaultBg));
                button.setTextColor(isSelected ? selectedText : defaultText);
                button.setStrokeColor(android.content.res.ColorStateList.valueOf(stroke));
                button.setStrokeWidth(dpToPx(1));
            }
        }
    }

    private void updatePartitionInfo() {
        String name = selectedPartition;
        if (name == null || name.isEmpty()) {
            tvPartitionInfo.setText("分区信息: 未选择");
            return;
        }
        String path = partitionPathMap.get(name);
        if (path == null) {
            path = "/dev/block/by-name/" + name;
        }

        final String partitionPath = path;
        tvPartitionInfo.setText("分区信息: " + name + "\n路径: " + partitionPath + "\n大小: 读取中...");
        new Thread(() -> {
            ShellResult sizeResult = runSuCommand("blockdev --getsize64 " + shellEscape(partitionPath));
            String sizeText = "未知";
            if (sizeResult.success) {
                try {
                    long bytes = Long.parseLong(sizeResult.stdout.trim());
                    sizeText = formatBytes(bytes);
                } catch (Exception ignored) {
                }
            }
            String finalSizeText = sizeText;
            runOnUiThread(() -> tvPartitionInfo.setText(
                    "分区信息: " + name + "\n路径: " + partitionPath + "\n大小: " + finalSizeText));
        }).start();
    }

    private void startExportPartition() {
        if (!ensurePartitionSelected()) {
            return;
        }
        String filename = selectedPartition + ".img";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        exportPartitionLauncher.launch(intent);
    }

    private void extractPartitionToUri(Uri outputUri) {
        String path = getSelectedPartitionPath();
        if (path == null) {
            return;
        }
        setBusy(true, "状态：正在提取分区...");
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "partition_dump_" + selectedPartition + ".img");
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }

            ShellResult dumpResult = runSuCommand("dd if=" + shellEscape(path) + " of=" + shellEscape(tmp.getAbsolutePath()) + " bs=4M");
            if (!dumpResult.success) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    tvPartitionOpStatus.setText("状态：提取失败");
                    Toast.makeText(this, "提取失败，请检查 Root 权限", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            boolean copied = copyFileToUri(tmp, outputUri);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            runOnUiThread(() -> {
                setBusy(false, null);
                if (copied) {
                    tvPartitionOpStatus.setText("状态：提取完成");
                    Toast.makeText(this, "分区提取成功", Toast.LENGTH_SHORT).show();
                } else {
                    tvPartitionOpStatus.setText("状态：提取失败（导出文件失败）");
                    Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void confirmWipePartition() {
        String path = getSelectedPartitionPath();
        if (path == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认清空分区")
                .setMessage("将清空分区 " + selectedPartition + " 的前 64MB，此操作不可逆。确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> wipePartition(path))
                .show();
    }

    private void wipePartition(String partitionPath) {
        setBusy(true, "状态：正在清空分区...");
        new Thread(() -> {
            ShellResult result = runSuCommand("dd if=/dev/zero of=" + shellEscape(partitionPath) + " bs=4M count=16 conv=fsync");
            runOnUiThread(() -> {
                setBusy(false, null);
                if (result.success) {
                    tvPartitionOpStatus.setText("状态：清空完成");
                    Toast.makeText(this, "分区已清空", Toast.LENGTH_SHORT).show();
                } else {
                    tvPartitionOpStatus.setText("状态：清空失败");
                    Toast.makeText(this, "清空失败，请检查 Root 权限", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void startPickImageForFlash() {
        if (!ensurePartitionSelected()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickImageLauncher.launch(intent);
    }

    private void flashPartitionFromUri(Uri imageUri) {
        String path = getSelectedPartitionPath();
        if (path == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认刷入分区")
                .setMessage("将把选择的镜像刷入分区 " + selectedPartition + "，此操作高风险。确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> doFlashPartition(path, imageUri))
                .show();
    }

    private void doFlashPartition(String partitionPath, Uri imageUri) {
        setBusy(true, "状态：正在刷入分区...");
        new Thread(() -> {
            File input = new File(getCacheDir(), "partition_flash_input.img");
            if (input.exists()) {
                //noinspection ResultOfMethodCallIgnored
                input.delete();
            }
            boolean copied = copyUriToFile(imageUri, input);
            if (!copied) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    tvPartitionOpStatus.setText("状态：刷入失败（读取镜像失败）");
                    Toast.makeText(this, "读取镜像失败", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            ShellResult result = runSuCommand("dd if=" + shellEscape(input.getAbsolutePath()) + " of=" + shellEscape(partitionPath) + " bs=4M conv=fsync");
            //noinspection ResultOfMethodCallIgnored
            input.delete();
            runOnUiThread(() -> {
                setBusy(false, null);
                if (result.success) {
                    tvPartitionOpStatus.setText("状态：刷入完成");
                    Toast.makeText(this, "分区刷入成功（重启设备生效）", Toast.LENGTH_SHORT).show();
                } else {
                    tvPartitionOpStatus.setText("状态：刷入失败");
                    Toast.makeText(this, "刷入失败，请检查 Root 权限", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String getSelectedPartitionPath() {
        if (!ensurePartitionSelected()) {
            return null;
        }
        String path = partitionPathMap.get(selectedPartition);
        if (path == null || path.isEmpty()) {
            path = "/dev/block/by-name/" + selectedPartition;
        }
        return path;
    }

    private boolean ensurePartitionSelected() {
        if (selectedPartition == null || selectedPartition.trim().isEmpty()) {
            Toast.makeText(this, "请先选择分区", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void setBusy(boolean busy, @Nullable String busyStatusText) {
        isBusy = busy;
        setPartitionButtonsEnabled(!busy);
        if (busy && busyStatusText != null) {
            tvPartitionOpStatus.setText(busyStatusText);
        }
    }

    private void showLoadingState(boolean loading, @Nullable String loadingText) {
        if (loading) {
            if (loadingText != null && tvPartitionLoading != null) {
                tvPartitionLoading.setText(loadingText);
            }
            if (progressPartitionLoading != null) {
                progressPartitionLoading.setIndeterminate(true);
            }
            if (layoutPartitionLoading != null) {
                layoutPartitionLoading.setVisibility(View.VISIBLE);
            }
            if (layoutPartitionContent != null) {
                layoutPartitionContent.setVisibility(View.GONE);
            }
            return;
        }

        if (layoutPartitionLoading != null) {
            layoutPartitionLoading.setVisibility(View.GONE);
        }
        if (layoutPartitionContent != null) {
            layoutPartitionContent.setVisibility(View.VISIBLE);
        }
    }

    private void setPartitionButtonsEnabled(boolean enabled) {
        for (int i = 0; i < layoutPartitionButtons.getChildCount(); i++) {
            View row = layoutPartitionButtons.getChildAt(i);
            if (!(row instanceof LinearLayout)) {
                continue;
            }
            LinearLayout rowLayout = (LinearLayout) row;
            for (int j = 0; j < rowLayout.getChildCount(); j++) {
                View child = rowLayout.getChildAt(j);
                if (child instanceof MaterialButton) {
                    child.setEnabled(enabled);
                }
            }
        }
    }

    private void updateTopHint() {
        if (tvPartitionHint == null) {
            return;
        }
        tvPartitionHint.setText("提示：长按分区名称可进行分区提取、写入、擦除操作\n当前系统槽位：" + getCurrentSlotText());
    }

    private String getCurrentSlotText() {
        String slotSuffix = readSystemProp("ro.boot.slot_suffix");
        if (slotSuffix == null || slotSuffix.trim().isEmpty()) {
            slotSuffix = readSystemProp("ro.boot.slot");
        }
        if (slotSuffix == null || slotSuffix.trim().isEmpty()) {
            return "未知";
        }
        String normalized = slotSuffix.trim().toLowerCase(Locale.ROOT);
        if ("_a".equals(normalized) || "a".equals(normalized)) {
            return "A 槽位";
        }
        if ("_b".equals(normalized) || "b".equals(normalized)) {
            return "B 槽位";
        }
        return slotSuffix;
    }

    private String readSystemProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double value = bytes;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024d;
            idx++;
        }
        return String.format(Locale.getDefault(), "%.2f %s (%d B)", value, units[idx], bytes);
    }

    private boolean copyFileToUri(File src, Uri dst) {
        try (FileInputStream fis = new FileInputStream(src);
             OutputStream os = getContentResolver().openOutputStream(dst)) {
            if (os == null) return false;
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean copyUriToFile(Uri src, File dst) {
        try (java.io.InputStream is = getContentResolver().openInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            if (is == null) return false;
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static ShellResult runSuCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exit = process.waitFor();
            return new ShellResult(exit == 0, stdout, stderr);
        } catch (Exception e) {
            return new ShellResult(false, "", e.getMessage() == null ? "" : e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readAll(java.io.InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    private static String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
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
