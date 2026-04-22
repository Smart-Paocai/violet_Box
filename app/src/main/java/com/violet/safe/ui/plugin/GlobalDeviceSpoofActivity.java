package com.violet.safe.ui.plugin;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.View;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GlobalDeviceSpoofActivity extends AppCompatActivity {

    private static final String MODULE_ID = "Device_Guise";
    private static final String MODULE_PROP_PRIMARY = "/data/adb/modules/" + MODULE_ID + "/system.prop";
    private static final String MODULE_PROP_UPDATE = "/data/adb/modules_update/" + MODULE_ID + "/system.prop";
    private static final String MODULE_PROP_FALLBACK = "/data/adb/violet_global_device_spoof/system.prop";
    private static final String PERSIST_SCRIPT_DIR = "/data/adb/service.d";
    private static final String PERSIST_SCRIPT_PATH = PERSIST_SCRIPT_DIR + "/99-violet-global-device-spoof.sh";
    private static final String PERSIST_POSTFS_DIR = "/data/adb/post-fs-data.d";
    private static final String PERSIST_POSTFS_PATH = PERSIST_POSTFS_DIR + "/99-violet-global-device-spoof.sh";
    private static final String PERSIST_MODULE_ID = "violet_global_device_spoof";
    private static final String PERSIST_MODULE_DIR = "/data/adb/modules/" + PERSIST_MODULE_ID;
    private static final String PERSIST_MODULE_PROP = PERSIST_MODULE_DIR + "/module.prop";
    private static final String PERSIST_SYSTEM_PROP = PERSIST_MODULE_DIR + "/system.prop";
    private static final String PREFS_NAME = "global_device_spoof";
    private static final String KEY_ORIGINAL_BRAND = "original_brand";
    private static final String KEY_ORIGINAL_MANUFACTURER = "original_manufacturer";
    private static final String KEY_ORIGINAL_MODEL = "original_model";
    private static final String KEY_ORIGINAL_DEVICE = "original_device";
    private static final String KEY_ORIGINAL_FINGERPRINT = "original_fingerprint";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView tvCurrentBrand;
    private TextView tvCurrentModel;
    private TextView tvCurrentDevice;
    private TextView tvCurrentFingerprint;
    private TextInputEditText etSpoofBrand;
    private TextInputEditText etSpoofManufacturer;
    private TextInputEditText etSpoofModel;
    private TextInputEditText etSpoofDevice;
    private TextInputEditText etSpoofFingerprint;
    private MaterialButton btnSaveOriginal;
    private MaterialButton btnRestoreOriginal;
    private MaterialButton btnApplySpoof;
    private View progress;
    private volatile String lastApplyError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_device_spoof);

        Toolbar toolbar = findViewById(R.id.toolbarGlobalDeviceSpoof);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("全局机型伪装");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCurrentBrand = findViewById(R.id.tvCurrentBrand);
        tvCurrentModel = findViewById(R.id.tvCurrentModel);
        tvCurrentDevice = findViewById(R.id.tvCurrentDevice);
        tvCurrentFingerprint = findViewById(R.id.tvCurrentFingerprint);
        etSpoofBrand = findViewById(R.id.etSpoofBrand);
        etSpoofManufacturer = findViewById(R.id.etSpoofManufacturer);
        etSpoofModel = findViewById(R.id.etSpoofModel);
        etSpoofDevice = findViewById(R.id.etSpoofDevice);
        etSpoofFingerprint = findViewById(R.id.etSpoofFingerprint);
        btnSaveOriginal = findViewById(R.id.btnSaveOriginalProps);
        btnRestoreOriginal = findViewById(R.id.btnRestoreOriginalProps);
        btnApplySpoof = findViewById(R.id.btnGlobalApplySpoof);
        progress = findViewById(R.id.progressGlobalDeviceSpoof);

        btnSaveOriginal.setOnClickListener(v -> saveOriginalProps());
        btnRestoreOriginal.setOnClickListener(v -> restoreOriginalProps());
        btnApplySpoof.setOnClickListener(v -> applyGlobalDeviceSpoof());

        loadDeviceInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (btnSaveOriginal != null) btnSaveOriginal.setEnabled(!busy);
        if (btnRestoreOriginal != null) btnRestoreOriginal.setEnabled(!busy);
        if (btnApplySpoof != null) btnApplySpoof.setEnabled(!busy);
        if (etSpoofBrand != null) etSpoofBrand.setEnabled(!busy);
        if (etSpoofManufacturer != null) etSpoofManufacturer.setEnabled(!busy);
        if (etSpoofModel != null) etSpoofModel.setEnabled(!busy);
        if (etSpoofDevice != null) etSpoofDevice.setEnabled(!busy);
        if (etSpoofFingerprint != null) etSpoofFingerprint.setEnabled(!busy);
    }

    private void loadDeviceInfo() {
        setBusy(true);
        ioExecutor.execute(() -> {
            String brand = trimOrEmpty(getPropViaSu("ro.product.brand"));
            String model = trimOrEmpty(getPropViaSu("ro.product.model"));
            String device = trimOrEmpty(getPropViaSu("ro.product.device"));
            String fingerprint = trimOrEmpty(getPropViaSu("ro.build.fingerprint"));
            runOnUiThread(() -> {
                setBusy(false);
                tvCurrentBrand.setText("当前品牌：" + valueOrDash(brand));
                tvCurrentModel.setText("当前机型：" + valueOrDash(model));
                tvCurrentDevice.setText("当前设备代号：" + valueOrDash(device));
                tvCurrentFingerprint.setText("当前指纹：" + valueOrDash(fingerprint));
            });
        });
    }

    private void saveOriginalProps() {
        setBusy(true);
        ioExecutor.execute(() -> {
            String brand = trimOrEmpty(getPropViaSu("ro.product.brand"));
            String manufacturer = trimOrEmpty(getPropViaSu("ro.product.manufacturer"));
            String model = trimOrEmpty(getPropViaSu("ro.product.model"));
            String device = trimOrEmpty(getPropViaSu("ro.product.device"));
            String fingerprint = trimOrEmpty(getPropViaSu("ro.build.fingerprint"));
            boolean valid = !brand.isEmpty() && !manufacturer.isEmpty() && !model.isEmpty()
                    && !device.isEmpty() && !fingerprint.isEmpty();
            if (valid) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_ORIGINAL_BRAND, brand)
                        .putString(KEY_ORIGINAL_MANUFACTURER, manufacturer)
                        .putString(KEY_ORIGINAL_MODEL, model)
                        .putString(KEY_ORIGINAL_DEVICE, device)
                        .putString(KEY_ORIGINAL_FINGERPRINT, fingerprint)
                        .apply();
            }
            runOnUiThread(() -> {
                setBusy(false);
                Toast.makeText(this, valid ? "原机属性已保存" : "保存失败，无法读取完整原机属性", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void restoreOriginalProps() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String brand = trimOrEmpty(prefs.getString(KEY_ORIGINAL_BRAND, ""));
        String manufacturer = trimOrEmpty(prefs.getString(KEY_ORIGINAL_MANUFACTURER, ""));
        String model = trimOrEmpty(prefs.getString(KEY_ORIGINAL_MODEL, ""));
        String device = trimOrEmpty(prefs.getString(KEY_ORIGINAL_DEVICE, ""));
        String fingerprint = trimOrEmpty(prefs.getString(KEY_ORIGINAL_FINGERPRINT, ""));
        if (brand.isEmpty() || manufacturer.isEmpty() || model.isEmpty() || device.isEmpty() || fingerprint.isEmpty()) {
            Toast.makeText(this, "未找到已保存的原机属性，请先点击保存", Toast.LENGTH_SHORT).show();
            return;
        }
        if (etSpoofBrand != null) etSpoofBrand.setText(brand);
        if (etSpoofManufacturer != null) etSpoofManufacturer.setText(manufacturer);
        if (etSpoofModel != null) etSpoofModel.setText(model);
        if (etSpoofDevice != null) etSpoofDevice.setText(device);
        if (etSpoofFingerprint != null) etSpoofFingerprint.setText(fingerprint);

        setBusy(true);
        ioExecutor.execute(() -> {
            boolean ok = writeAndApplyProps(brand, manufacturer, model, device, fingerprint);
            runOnUiThread(() -> {
                setBusy(false);
                if (ok) {
                    Toast.makeText(this, "已还原为保存的原机属性", Toast.LENGTH_SHORT).show();
                    loadDeviceInfo();
                } else {
                    String detail = trimOrEmpty(lastApplyError);
                    if (detail.length() > 80) detail = detail.substring(0, 80) + "...";
                    String msg = detail.isEmpty() ? "还原失败，请检查 ROOT 与开机脚本环境" : "还原失败：" + detail;
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void applyGlobalDeviceSpoof() {
        final String brand = normalizeInput(etSpoofBrand);
        final String manufacturer = normalizeInput(etSpoofManufacturer);
        final String model = normalizeInput(etSpoofModel);
        final String device = normalizeInput(etSpoofDevice);
        final String fingerprint = normalizeInput(etSpoofFingerprint);
        if (brand.isEmpty() || manufacturer.isEmpty() || model.isEmpty() || device.isEmpty()) {
            Toast.makeText(this, "请填写品牌、制造商、机型和设备代号", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        ioExecutor.execute(() -> {
            boolean ok = writeAndApplyProps(brand, manufacturer, model, device, fingerprint);
            runOnUiThread(() -> {
                setBusy(false);
                if (ok) {
                    Toast.makeText(this, "全局机型伪装已应用", Toast.LENGTH_SHORT).show();
                    loadDeviceInfo();
                } else {
                    String detail = trimOrEmpty(lastApplyError);
                    if (detail.length() > 80) detail = detail.substring(0, 80) + "...";
                    String msg = detail.isEmpty()
                            ? "应用失败，请检查 ROOT 与开机脚本环境"
                            : "应用失败：" + detail;
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private boolean writeAndApplyProps(String brand, String manufacturer, String model, String device, String fingerprint) {
        String targetProp = resolveWritableSystemPropPath();
        Map<String, String> props = new LinkedHashMap<>();
        props.put("ro.product.brand", brand);
        props.put("ro.product.manufacturer", manufacturer);
        props.put("ro.product.model", model);
        props.put("ro.product.device", device);
        props.put("ro.product.name", device);
        props.put("ro.product.marketname", model);
        props.put("ro.product.system.brand", brand);
        props.put("ro.product.system.name", device);
        props.put("ro.product.system.device", device);
        props.put("ro.build.product", device);
        if (!trimOrEmpty(fingerprint).isEmpty()) {
            props.put("ro.build.fingerprint", fingerprint);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SCRIPT_DIR=\"").append(PERSIST_SCRIPT_DIR).append("\"; ");
        sb.append("SCRIPT_PATH=\"").append(PERSIST_SCRIPT_PATH).append("\"; ");
        sb.append("POSTFS_DIR=\"").append(PERSIST_POSTFS_DIR).append("\"; ");
        sb.append("POSTFS_PATH=\"").append(PERSIST_POSTFS_PATH).append("\"; ");
        sb.append("mkdir -p \"$SCRIPT_DIR\"; ");
        sb.append(": > \"$SCRIPT_PATH\"; ");
        sb.append("echo \"#!/system/bin/sh\" > \"$SCRIPT_PATH\"; ");
        sb.append("echo \"# Auto generated by Violet\" >> \"$SCRIPT_PATH\"; ");
        sb.append("PERSIST_DIR=\"").append(PERSIST_MODULE_DIR).append("\"; ");
        sb.append("mkdir -p \"$PERSIST_DIR\"; ");
        sb.append("echo \"id=").append(PERSIST_MODULE_ID).append("\" > \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("echo \"name=Violet Global Device Spoof\" >> \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("echo \"version=1.0\" >> \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("echo \"versionCode=1\" >> \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("echo \"author=Violet\" >> \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("echo \"description=Global device property spoof managed by app\" >> \"").append(PERSIST_MODULE_PROP).append("\"; ");
        sb.append("touch \"$PERSIST_DIR/auto_mount\"; ");
        sb.append("touch \"$PERSIST_DIR/update\"; ");
        sb.append(": > \"").append(PERSIST_SYSTEM_PROP).append("\"; ");
        sb.append("TARGET_PROP=\"").append(targetProp).append("\"; ");
        sb.append("mkdir -p \"$(dirname \"$TARGET_PROP\")\"; ");
        sb.append(": > \"$TARGET_PROP\"; ");
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            String valueEsc = shellDoubleQuoteEscape(entry.getValue());
            sb.append("echo \"").append(key).append("=").append(valueEsc).append("\" >> \"$TARGET_PROP\"; ");
            sb.append("echo \"").append(key).append("=").append(valueEsc).append("\" >> \"").append(PERSIST_SYSTEM_PROP).append("\"; ");
            sb.append("echo \"resetprop ").append(key).append(" \\\"").append(valueEsc).append("\\\"\" >> \"$SCRIPT_PATH\"; ");
            sb.append("resetprop ").append(key).append(" \"").append(valueEsc).append("\"; ");
        }
        sb.append("chmod 0755 \"$SCRIPT_PATH\"; ");
        sb.append("if [ -d \"$POSTFS_DIR\" ]; then cp -f \"$SCRIPT_PATH\" \"$POSTFS_PATH\"; chmod 0755 \"$POSTFS_PATH\"; fi; ");

        ShellExecResult result = runShellViaSuDetailed(sb.toString(), 9000);
        lastApplyError = result.summary;
        return result.success;
    }

    private String resolveWritableSystemPropPath() {
        if (isDirectoryExistsViaSu("/data/adb/modules/" + MODULE_ID)) return MODULE_PROP_PRIMARY;
        if (isDirectoryExistsViaSu("/data/adb/modules_update/" + MODULE_ID)) return MODULE_PROP_UPDATE;
        return MODULE_PROP_FALLBACK;
    }

    private String getPropViaSu(String key) {
        String cmd = "getprop " + key;
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd).redirectErrorStream(true).start();
                boolean finished = process.waitFor(1800, TimeUnit.MILLISECONDS);
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

    private boolean isDirectoryExistsViaSu(String absolutePath) {
        String cmd = "[ -d \"" + absolutePath + "\" ]";
        return runShellViaSu(cmd, 1200);
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

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String valueOrDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    private static String shellDoubleQuoteEscape(String src) {
        if (src == null) return "";
        return src.replace("\\", "\\\\").replace("\"", "\\\"");
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
