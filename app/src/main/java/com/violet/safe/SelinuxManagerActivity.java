package com.violet.safe;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SelinuxManagerActivity extends AppCompatActivity {

    public static final String PREF_NAME = "selinux_manager_pref";
    public static final String KEY_AUTO_ENABLED = "auto_enabled";
    public static final String KEY_TARGET_MODE = "target_mode";
    public static final String MODE_ENFORCING = "enforcing";
    public static final String MODE_PERMISSIVE = "permissive";

    private TextView tvSelinuxModeValue;
    private MaterialButton btnToggleSelinux;
    private SwitchMaterial switchAutoSelinux;
    private RadioGroup radioSelinuxTarget;
    private RadioButton radioEnforcing;
    private RadioButton radioPermissive;
    private MaterialButton btnSaveAutoSelinux;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selinux_manager);

        Toolbar toolbar = findViewById(R.id.toolbarSelinux);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("SELinux管理");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSelinuxModeValue = findViewById(R.id.tvSelinuxModeValue);
        btnToggleSelinux = findViewById(R.id.btnToggleSelinux);
        switchAutoSelinux = findViewById(R.id.switchAutoSelinux);
        radioSelinuxTarget = findViewById(R.id.radioSelinuxTarget);
        radioEnforcing = findViewById(R.id.radioEnforcing);
        radioPermissive = findViewById(R.id.radioPermissive);
        btnSaveAutoSelinux = findViewById(R.id.btnSaveAutoSelinux);

        loadAutoToggleSettings();
        refreshCurrentMode();

        btnToggleSelinux.setOnClickListener(v -> toggleSelinuxMode());
        btnSaveAutoSelinux.setOnClickListener(v -> saveAutoToggleSettings());
    }

    private void loadAutoToggleSettings() {
        boolean enabled = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getBoolean(KEY_AUTO_ENABLED, false);
        String mode = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_TARGET_MODE, MODE_ENFORCING);
        switchAutoSelinux.setChecked(enabled);
        if (MODE_PERMISSIVE.equals(mode)) {
            radioPermissive.setChecked(true);
        } else {
            radioEnforcing.setChecked(true);
        }
    }

    private void saveAutoToggleSettings() {
        String targetMode = radioSelinuxTarget.getCheckedRadioButtonId() == R.id.radioPermissive
                ? MODE_PERMISSIVE : MODE_ENFORCING;

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_ENABLED, switchAutoSelinux.isChecked())
                .putString(KEY_TARGET_MODE, targetMode)
                .apply();

        Toast.makeText(this, "开机自动切换设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void refreshCurrentMode() {
        tvSelinuxModeValue.setText("读取中...");
        btnToggleSelinux.setEnabled(false);

        new Thread(() -> {
            ShellResult result = runSuCommand("getenforce");
            runOnUiThread(() -> {
                if (!result.success) {
                    tvSelinuxModeValue.setText("读取失败（请确认已授予Root权限）");
                    btnToggleSelinux.setEnabled(false);
                    btnToggleSelinux.setText("切换模式");
                    return;
                }

                String modeRaw = result.stdout.trim();
                String mode = normalizeMode(modeRaw);
                if (MODE_ENFORCING.equals(mode)) {
                    tvSelinuxModeValue.setText("严格模式");
                    btnToggleSelinux.setText("切换到宽容模式");
                    btnToggleSelinux.setEnabled(true);
                } else if (MODE_PERMISSIVE.equals(mode)) {
                    tvSelinuxModeValue.setText("宽容模式");
                    btnToggleSelinux.setText("切换到严格模式");
                    btnToggleSelinux.setEnabled(true);
                } else {
                    tvSelinuxModeValue.setText("未知模式: " + modeRaw);
                    btnToggleSelinux.setEnabled(false);
                    btnToggleSelinux.setText("切换模式");
                }
            });
        }).start();
    }

    private void toggleSelinuxMode() {
        btnToggleSelinux.setEnabled(false);
        String display = tvSelinuxModeValue.getText().toString();
        boolean toPermissive = display.contains("严格");
        String command = toPermissive ? "setenforce 0" : "setenforce 1";

        new Thread(() -> {
            ShellResult result = runSuCommand(command);
            runOnUiThread(() -> {
                if (result.success) {
                    Toast.makeText(this, "SELinux 模式切换成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "切换失败，请检查Root权限", Toast.LENGTH_SHORT).show();
                }
                refreshCurrentMode();
            });
        }).start();
    }

    public static boolean applySelinuxModeForBoot(android.content.Context context) {
        boolean enabled = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_ENABLED, false);
        if (!enabled) {
            return false;
        }

        String mode = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getString(KEY_TARGET_MODE, MODE_ENFORCING);
        String command = MODE_PERMISSIVE.equals(mode) ? "setenforce 0" : "setenforce 1";
        ShellResult result = runSuCommand(command);
        return result.success;
    }

    private static String normalizeMode(String modeRaw) {
        if (modeRaw == null) {
            return "";
        }
        String lower = modeRaw.trim().toLowerCase();
        if (lower.startsWith("enforcing")) {
            return MODE_ENFORCING;
        }
        if (lower.startsWith("permissive")) {
            return MODE_PERMISSIVE;
        }
        return lower;
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
        } catch (Exception ignored) {
        }
        return sb.toString();
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
