package com.violet.safe.ui.payload;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;
import com.violet.safe.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PayloadActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView tvSource;
    private TextView tvResult;
    private LinearLayout partitionContainer;
    private MaterialButton btnExtract;
    private MaterialButton btnParse;
    private MaterialButton btnInputAction;
    private EditText etUrl;
    private EditText etSearch;
    private CheckBox cbSelectAll;
    private ProgressBar progressExtract;
    private TextView tvProgress;
    private boolean suppressSelectAllCallback;

    @Nullable
    private PayloadCore.PayloadSession currentSession;
    @Nullable
    private String currentSourceName;
    private final List<PayloadCore.PartitionInfoRow> allPartitionRows = new ArrayList<>();
    private final Set<String> selectedPartitionNames = new LinkedHashSet<>();
    private List<String> pendingExtractPartitions = new ArrayList<>();

    private final ActivityResultLauncher<Uri> openOutputDirLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), treeUri -> {
                try {
                    List<String> partitions = new ArrayList<>(pendingExtractPartitions);
                    pendingExtractPartitions = new ArrayList<>();
                    if (partitions.isEmpty()) {
                        return;
                    }
                    if (treeUri == null) {
                        Toast.makeText(this, "未选择保存位置", Toast.LENGTH_SHORT).show();
                        btnExtract.setEnabled(true);
                        return;
                    }
                    DocumentFile targetDir = DocumentFile.fromTreeUri(this, treeUri);
                    if (targetDir == null || !targetDir.canWrite()) {
                        Toast.makeText(this, "所选目录不可写", Toast.LENGTH_SHORT).show();
                        btnExtract.setEnabled(true);
                        return;
                    }
                    extractPartitionsToTree(partitions, targetDir);
                } catch (Throwable t) {
                    pendingExtractPartitions = new ArrayList<>();
                    btnExtract.setEnabled(true);
                    String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    tvResult.append("\n提取失败: 保存位置选择异常 - " + msg);
                    Toast.makeText(this, "保存位置选择失败", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payload);

        Toolbar toolbar = findViewById(R.id.toolbarPayload);
        setSupportActionBar(toolbar);
        toolbar.setTitle("OTA云提取");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("OTA云提取");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSource = findViewById(R.id.tvPayloadSource);
        tvResult = findViewById(R.id.tvPayloadResult);
        partitionContainer = findViewById(R.id.layoutPayloadPartitions);
        btnExtract = findViewById(R.id.btnPayloadExtract);
        btnParse = findViewById(R.id.btnPayloadParse);
        btnInputAction = findViewById(R.id.btnPayloadParseUrl);
        etUrl = findViewById(R.id.etPayloadUrl);
        etSearch = findViewById(R.id.etPayloadSearch);
        cbSelectAll = findViewById(R.id.cbPayloadSelectAll);
        progressExtract = findViewById(R.id.progressPayloadExtract);
        tvProgress = findViewById(R.id.tvPayloadProgress);
        btnInputAction.setOnClickListener(v -> pasteUrlFromClipboard());
        btnParse.setOnClickListener(v -> parseCurrentSource());
        btnExtract.setOnClickListener(v -> startExtractSelectedPartitions());
        btnExtract.setEnabled(false);
        tvSource.setText("来源: 云端 URL");
        updateExtractProgressUi(0L, 0L);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSelectAllCallback) {
                return;
            }
            if (isChecked) {
                selectedPartitionNames.clear();
                for (PayloadCore.PartitionInfoRow row : allPartitionRows) {
                    selectedPartitionNames.add(row.name);
                }
            } else {
                selectedPartitionNames.clear();
            }
            renderPartitionList(filterRows(allPartitionRows, etSearch.getText() == null ? "" : etSearch.getText().toString()));
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderPartitionList(filterRows(allPartitionRows, s == null ? "" : s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        renderPartitionList(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
        closeCurrentSession();
    }

    private void pasteUrlFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        if (text == null) {
            Toast.makeText(this, "剪贴板内容不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = text.toString().trim();
        etUrl.setText(url);
        if (url.isEmpty()) {
            Toast.makeText(this, "剪贴板没有 URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseCurrentSource() {
        final String parseSource = etUrl.getText() == null ? "" : etUrl.getText().toString().trim();
        if (!PayloadCore.isValidUrl(parseSource)) {
            currentSourceName = null;
            tvSource.setText("来源: 云端 URL");
            Toast.makeText(this, "URL 无效，请输入完整的 http(s) 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        currentSourceName = parseSource;

        tvResult.setText("正在解析远程 payload...");
        btnExtract.setEnabled(false);
        updateExtractProgressUi(0L, 0L);
        renderPartitionList(null);

        ioExecutor.execute(() -> {
            try {
                closeCurrentSession();
                currentSession = PayloadCore.parseFromUrl(parseSource, null);
                List<PayloadCore.PartitionInfoRow> rows = PayloadCore.getPartitionInfoList(currentSession);
                runOnUiThread(() -> {
                    tvResult.setText(buildSessionText(currentSession, rows));
                    allPartitionRows.clear();
                    allPartitionRows.addAll(rows);
                    selectedPartitionNames.clear();
                    setSelectAllChecked(false);
                    renderPartitionList(filterRows(allPartitionRows, etSearch.getText() == null ? "" : etSearch.getText().toString()));
                    btnExtract.setEnabled(!rows.isEmpty());
                });
            } catch (Throwable t) {
                runOnUiThread(() -> tvResult.setText("解析失败: " + buildErrorMessage(t)));
            }
        });
    }

    private void startExtractSelectedPartitions() {
        PayloadCore.PayloadSession session = currentSession;
        if (session == null) {
            Toast.makeText(this, "请先解析 payload", Toast.LENGTH_SHORT).show();
            return;
        }
        if (allPartitionRows.isEmpty()) {
            Toast.makeText(this, "没有可提取分区", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedPartitionNames.isEmpty()) {
            Toast.makeText(this, "请先勾选分区", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingExtractPartitions = new ArrayList<>(selectedPartitionNames);
        openOutputDirLauncher.launch(null);
    }

    private void extractPartitionsToTree(List<String> partitions, DocumentFile targetDir) {
        PayloadCore.PayloadSession session = currentSession;
        if (session == null) return;
        btnExtract.setEnabled(false);
        tvResult.append("\n\n开始提取分区数量: " + partitions.size());

        ioExecutor.execute(() -> {
            long totalBytes = sumPartitionSizes(partitions);
            final long[] completedBytes = {0L};
            runOnUiThread(() -> updateExtractProgressUi(0L, totalBytes));
            File tempDir = new File(getCacheDir(), "payload_extract");
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                runOnUiThread(() -> {
                    tvResult.append("\n提取失败: 无法创建临时目录");
                    btnExtract.setEnabled(true);
                });
                return;
            }
            try {
                for (int i = 0; i < partitions.size(); i++) {
                    String partitionName = partitions.get(i);
                    int index = i + 1;
                    long partitionSize = findPartitionSize(partitionName);
                    File tempOutFile = new File(tempDir, partitionName + ".img");
                    PayloadCore.extractPartition(session, partitionName, tempDir.getAbsolutePath(), (bytes, outPath) ->
                            runOnUiThread(() -> {
                                tvResult.setText("提取中(" + index + "/" + partitions.size() + "): " + partitionName + "\n已写入: " + formatSize(bytes));
                                long currentDone = completedBytes[0] + Math.min(bytes, partitionSize);
                                updateExtractProgressUi(currentDone, totalBytes);
                            }));
                    DocumentFile existing = targetDir.findFile(partitionName + ".img");
                    if (existing != null) {
                        existing.delete();
                    }
                    DocumentFile targetFile = targetDir.createFile("application/octet-stream", partitionName + ".img");
                    if (targetFile == null || targetFile.getUri() == null) {
                        throw new IOException("无法创建目标文件: " + partitionName + ".img");
                    }
                    copyToUri(tempOutFile, targetFile.getUri());
                    if (tempOutFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        tempOutFile.delete();
                    }
                    completedBytes[0] += partitionSize;
                    runOnUiThread(() -> updateExtractProgressUi(completedBytes[0], totalBytes));
                }
                runOnUiThread(() -> {
                    tvResult.append("\n提取完成，共 " + partitions.size() + " 个分区");
                    updateExtractProgressUi(totalBytes, totalBytes);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    tvResult.append("\n提取失败: " + buildErrorMessage(t));
                    btnExtract.setEnabled(true);
                });
            } finally {
                File[] leftovers = tempDir.listFiles();
                if (leftovers != null) {
                    for (File file : leftovers) {
                        if (file != null && file.isFile()) {
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                        }
                    }
                }
                runOnUiThread(() -> btnExtract.setEnabled(!allPartitionRows.isEmpty()));
            }
        });
    }

    private void renderPartitionList(@Nullable List<PayloadCore.PartitionInfoRow> rows) {
        partitionContainer.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            TextView empty = buildRow("请先进行解析");
            partitionContainer.addView(empty);
            return;
        }
        for (PayloadCore.PartitionInfoRow row : rows) {
            partitionContainer.addView(buildPartitionRow(row.name, row.name + " · " + formatSize(row.size)));
        }
        syncSelectAllState();
    }

    private LinearLayout buildPartitionRow(String partitionName, String displayText) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(8);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        row.setBackgroundResource(R.drawable.fluent_cell_bg);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(selectedPartitionNames.contains(partitionName));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedPartitionNames.add(partitionName);
            } else {
                selectedPartitionNames.remove(partitionName);
            }
            syncSelectAllState();
        });
        row.addView(checkBox);

        TextView label = new TextView(this);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(textLp);
        label.setText(displayText);
        label.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_800));
        label.setTextSize(13f);
        label.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
        row.addView(label);
        return row;
    }

    private List<PayloadCore.PartitionInfoRow> filterRows(List<PayloadCore.PartitionInfoRow> src, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) {
            return new ArrayList<>(src);
        }
        List<PayloadCore.PartitionInfoRow> out = new ArrayList<>();
        for (PayloadCore.PartitionInfoRow row : src) {
            if (row.name != null && row.name.toLowerCase(Locale.US).contains(q)) {
                out.add(row);
            }
        }
        return out;
    }

    private long findPartitionSize(String partitionName) {
        for (PayloadCore.PartitionInfoRow row : allPartitionRows) {
            if (partitionName.equals(row.name)) {
                return Math.max(0L, row.size);
            }
        }
        return 0L;
    }

    private long sumPartitionSizes(List<String> partitionNames) {
        long total = 0L;
        for (String name : partitionNames) {
            total += findPartitionSize(name);
        }
        return total;
    }

    private TextView buildRow(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(8);
        tv.setLayoutParams(lp);
        tv.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        tv.setTextColor(ContextCompat.getColor(this, R.color.explore_slate_800));
        tv.setTextSize(13f);
        tv.setBackgroundResource(R.drawable.fluent_cell_bg);
        tv.setText(text);
        return tv;
    }

    private String buildSessionText(PayloadCore.PayloadSession session, List<PayloadCore.PartitionInfoRow> rows) {
        return "来源: " + session.fileName + "\n"
                + "包体大小: " + formatSize(session.archiveSize) + "\n"
                + "数据偏移: " + session.dataOffset + "\n"
                + "块大小: " + session.blockSize + "\n"
                + "安全补丁: " + session.getSecurityPatchLevel() + "\n"
                + "分区数量: " + rows.size();
    }

    private void closeCurrentSession() {
        if (currentSession != null) {
            try {
                currentSession.close();
            } catch (IOException ignored) {
            }
        }
        currentSession = null;
    }

    private void copyToUri(File sourceFile, Uri targetUri) throws IOException {
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = getContentResolver().openOutputStream(targetUri, "w")) {
            if (out == null) {
                throw new IOException("无法打开目标输出流");
            }
            byte[] buffer = new byte[1024 * 128];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static String buildErrorMessage(Throwable t) {
        if (t == null) {
            return "未知异常";
        }
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }

    private void updateExtractProgressUi(long doneBytes, long totalBytes) {
        long safeDone = Math.max(0L, doneBytes);
        long safeTotal = Math.max(0L, totalBytes);
        int percent = safeTotal <= 0 ? 0 : (int) Math.min(100L, (safeDone * 100L) / safeTotal);
        progressExtract.setProgress(percent);
        if (safeTotal <= 0) {
            tvProgress.setText("进度：0%");
        } else {
            tvProgress.setText("进度：" + percent + "%（" + formatSize(safeDone) + " / " + formatSize(safeTotal) + "）");
        }
    }

    private void syncSelectAllState() {
        boolean shouldChecked = !allPartitionRows.isEmpty() && selectedPartitionNames.size() == allPartitionRows.size();
        setSelectAllChecked(shouldChecked);
    }

    private void setSelectAllChecked(boolean checked) {
        suppressSelectAllCallback = true;
        cbSelectAll.setChecked(checked);
        suppressSelectAllCallback = false;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
