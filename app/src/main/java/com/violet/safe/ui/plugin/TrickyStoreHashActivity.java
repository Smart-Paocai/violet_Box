package com.violet.safe.ui.plugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrickyStoreHashActivity extends AppCompatActivity {

    private static final String TRICKY_STORE_HASH_FILE = "/data/adb/boot_hash";
    private static final String PREF_HASH_HISTORY = "tricky_store_hash_history";
    private static final String KEY_LAST_HASH = "last_hash";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextInputEditText etHashValue;
    private android.widget.TextView tvCurrentHashValue;
    private android.widget.TextView tvHistoryHashValue;
    private View progress;
    private MaterialButton btnPaste;
    private MaterialButton btnSave;
    private String currentHashValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tricky_store_hash);

        Toolbar toolbar = findViewById(R.id.toolbarTrickyStoreHash);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tricky Store");
            getSupportActionBar().setSubtitle("哈希值");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etHashValue = findViewById(R.id.etHashValue);
        tvCurrentHashValue = findViewById(R.id.tvCurrentHashValue);
        tvHistoryHashValue = findViewById(R.id.tvHistoryHashValue);
        progress = findViewById(R.id.progressHashValue);
        btnPaste = findViewById(R.id.btnHashPaste);
        btnSave = findViewById(R.id.btnHashSave);

        btnPaste.setOnClickListener(v -> pasteHashValueFromClipboard());
        btnSave.setOnClickListener(v -> saveHashValue());
        if (tvCurrentHashValue != null) {
            tvCurrentHashValue.setOnClickListener(v ->
                    copyHashValueToClipboard(tvCurrentHashValue.getText() == null ? "" : tvCurrentHashValue.getText().toString()));
        }
        if (tvHistoryHashValue != null) {
            tvHistoryHashValue.setOnClickListener(v ->
                    copyHashValueToClipboard(tvHistoryHashValue.getText() == null ? "" : tvHistoryHashValue.getText().toString()));
        }

        updateHistoryHashValueDisplay();
        loadHashValue();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (btnPaste != null) btnPaste.setEnabled(!busy);
        if (btnSave != null) btnSave.setEnabled(!busy);
        if (etHashValue != null) etHashValue.setEnabled(!busy);
    }

    private void loadHashValue() {
        setBusy(true);
        ioExecutor.execute(() -> {
            String content = readTextFileViaSu(TRICKY_STORE_HASH_FILE);
            runOnUiThread(() -> {
                setBusy(false);
                currentHashValue = content == null ? "" : content.trim();
                String value = currentHashValue.isEmpty() ? "—" : currentHashValue;
                if (tvCurrentHashValue != null) tvCurrentHashValue.setText(value);
                if (etHashValue != null) etHashValue.setText("");
                android.widget.Toast.makeText(this, content == null ? "读取失败" : "已读取", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void saveHashValue() {
        setBusy(true);
        final String out = etHashValue == null || etHashValue.getText() == null
                ? ""
                : etHashValue.getText().toString().trim();
        ioExecutor.execute(() -> {
            boolean ok = writeTextFileViaSu(TRICKY_STORE_HASH_FILE, out);
            runOnUiThread(() -> {
                setBusy(false);
                if (ok) {
                    saveHistoryHashValue(currentHashValue);
                    currentHashValue = out;
                    if (tvCurrentHashValue != null) {
                        tvCurrentHashValue.setText(currentHashValue.isEmpty() ? "—" : currentHashValue);
                    }
                    updateHistoryHashValueDisplay();
                }
                android.widget.Toast.makeText(this, ok ? "保存成功" : "保存失败", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void pasteHashValueFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            android.widget.Toast.makeText(this, "剪贴板为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            android.widget.Toast.makeText(this, "剪贴板为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        String pasted = text == null ? "" : text.toString().trim();
        if (pasted.isEmpty()) {
            android.widget.Toast.makeText(this, "剪贴板内容为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (etHashValue != null) {
            etHashValue.setText(pasted);
            etHashValue.setSelection(pasted.length());
        }
        android.widget.Toast.makeText(this, "已粘贴", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void copyHashValueToClipboard(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || "—".equals(normalized)) {
            android.widget.Toast.makeText(this, "没有可复制的内容", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            android.widget.Toast.makeText(this, "复制失败", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("hash", normalized));
        android.widget.Toast.makeText(this, "已复制", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void saveHistoryHashValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        getSharedPreferences(PREF_HASH_HISTORY, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_HASH, value)
                .apply();
    }

    private void updateHistoryHashValueDisplay() {
        String history = getSharedPreferences(PREF_HASH_HISTORY, MODE_PRIVATE)
                .getString(KEY_LAST_HASH, "");
        if (tvHistoryHashValue != null) {
            tvHistoryHashValue.setText(TextUtils.isEmpty(history) ? "—" : history);
        }
    }

    private String readTextFileViaSu(String absolutePath) {
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "cat \"" + absolutePath + "\"")
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroy();
                    continue;
                }
                if (process.exitValue() != 0) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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

    private boolean writeTextFileViaSu(String absolutePath, String content) {
        String safe = content == null ? "" : content;
        if (!safe.endsWith("\n")) {
            safe = safe + "\n";
        }
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "cat > \"" + absolutePath + "\"")
                        .redirectErrorStream(true)
                        .start();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(safe);
                    writer.flush();
                }
                boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
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
