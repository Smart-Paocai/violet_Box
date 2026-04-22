package com.violet.safe.ui.plugin;

import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.violet.safe.R;
import com.violet.safe.core.util.SelinuxShellUtil;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceIdModifyActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "device_id_modify";
    private static final String KEY_SPOOF_ANDROID_ID = "spoof_android_id";
    private static final String KEY_BACKUP_ANDROID_ID = "backup_android_id";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView tvCurrentAndroidId;
    private TextInputEditText etNewAndroidId;
    private MaterialButton btnFillCurrent;
    private MaterialButton btnBackupCurrent;
    private MaterialButton btnRandom;
    private MaterialButton btnApply;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_id_modify);

        Toolbar toolbar = findViewById(R.id.toolbarDeviceIdModify);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("设备ID修改");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCurrentAndroidId = findViewById(R.id.tvCurrentAndroidId);
        etNewAndroidId = findViewById(R.id.etNewAndroidId);
        btnFillCurrent = findViewById(R.id.btnFillCurrentAndroidId);
        btnBackupCurrent = findViewById(R.id.btnBackupCurrentAndroidId);
        btnRandom = findViewById(R.id.btnRandomAndroidId);
        btnApply = findViewById(R.id.btnApplyAndroidId);
        progress = findViewById(R.id.progressDeviceId);

        btnFillCurrent.setOnClickListener(v -> restoreBackupAndroidIdToInput());

        btnBackupCurrent.setOnClickListener(v -> backupCurrentAndroidId());

        btnRandom.setOnClickListener(v -> {
            String r = randomAndroidIdHex16();
            if (etNewAndroidId != null) etNewAndroidId.setText(r);
        });

        btnApply.setOnClickListener(v -> applyAndroidId());

        refreshCurrentAndroidIdUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentAndroidIdUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (btnApply != null) btnApply.setEnabled(!busy);
        if (btnFillCurrent != null) btnFillCurrent.setEnabled(!busy);
        if (btnBackupCurrent != null) btnBackupCurrent.setEnabled(!busy);
        if (btnRandom != null) btnRandom.setEnabled(!busy);
        if (etNewAndroidId != null) etNewAndroidId.setEnabled(!busy);
    }

    private String readCurrentAndroidId() {
        try {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return id != null ? id : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void refreshCurrentAndroidIdUi() {
        if (tvCurrentAndroidId == null) return;
        ioExecutor.execute(() -> {
            String cur = readCurrentAndroidIdViaSu();
            if (TextUtils.isEmpty(cur)) {
                cur = readCurrentAndroidId();
            }
            final String show = cur;
            runOnUiThread(() -> tvCurrentAndroidId.setText(
                    TextUtils.isEmpty(show) ? "当前安卓ID：—" : ("" + show)
            ));
        });
    }

    private String readCurrentAndroidIdViaSu() {
        SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu("settings get secure android_id", 1500);
        if (!r.success || r.stdout == null) {
            return "";
        }
        String value = r.stdout.trim();
        if ("null".equalsIgnoreCase(value) || value.isEmpty()) {
            return "";
        }
        return value;
    }

    private void backupCurrentAndroidId() {
        String cur = readCurrentAndroidIdViaSu();
        if (TextUtils.isEmpty(cur)) {
            cur = readCurrentAndroidId();
        }
        if (TextUtils.isEmpty(cur)) {
            Toast.makeText(this, "读取当前 ANDROID_ID 失败", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP_ANDROID_ID, cur)
                .apply();
        Toast.makeText(this, "已备份当前 ANDROID_ID", Toast.LENGTH_SHORT).show();
    }

    private void restoreBackupAndroidIdToInput() {
        String backup = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_BACKUP_ANDROID_ID, "");
        if (TextUtils.isEmpty(backup)) {
            Toast.makeText(this, "暂无备份ID，请先点击“备份当前ID”", Toast.LENGTH_SHORT).show();
            return;
        }
        if (etNewAndroidId != null) {
            etNewAndroidId.setText(backup);
        }
        applyAndroidId();
    }

    private void applyAndroidId() {
        final String newId = normalizeInput(etNewAndroidId);
        if (newId.isEmpty()) {
            Toast.makeText(this, "请输入要修改的设备ID", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newId.matches("(?i)^[0-9a-f]{16}$")) {
            Toast.makeText(this, "ANDROID_ID 建议为 16 位十六进制（0-9a-f）", Toast.LENGTH_LONG).show();
            // 仍允许继续保存/尝试写入
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_SPOOF_ANDROID_ID, newId)
                .apply();

        setBusy(true);
        ioExecutor.execute(() -> {
            String cmd = buildSettingsPutAndroidIdCommand(newId);
            SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu(cmd, 1500);
            runOnUiThread(() -> {
                setBusy(false);
                if (r.success) {
                    Toast.makeText(this, "应用成功，重启设备后生效", Toast.LENGTH_LONG).show();
                } else {
                    String err = (r.stderr != null ? r.stderr.trim() : "");
                    if (err.length() > 80) err = err.substring(0, 80) + "...";
                    String msg = TextUtils.isEmpty(err)
                            ? "写入失败：请确认已授予 ROOT 权限"
                            : ("写入失败：" + err);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                refreshCurrentAndroidIdUi();
            });
        });
    }

    private static String normalizeInput(TextInputEditText et) {
        if (et == null) return "";
        CharSequence cs = et.getText();
        if (cs == null) return "";
        return cs.toString().trim();
    }

    private static String buildSettingsPutAndroidIdCommand(String newId) {
        String safe = shellEscapeSingleQuotes(newId);
        // Android 8+ 上 ANDROID_ID 通常为 per-app SSAID，settings 写入未必生效，但可以作为“尽力而为”的尝试。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return String.format(Locale.US, "settings put secure android_id '%s'", safe);
        }
        return String.format(Locale.US, "settings put secure android_id '%s'", safe);
    }

    private static String randomAndroidIdHex16() {
        byte[] b = new byte[8];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(16);
        for (byte v : b) {
            sb.append(String.format(Locale.US, "%02x", v));
        }
        return sb.toString();
    }

    private static String shellEscapeSingleQuotes(String s) {
        if (s == null) return "";
        return s.replace("'", "'\"'\"'");
    }
}

