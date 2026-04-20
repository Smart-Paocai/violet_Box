package com.violet.safe.ui.plugin;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrickyStoreSecurityPatchActivity extends AppCompatActivity {

    private static final String TRICKY_STORE_SECURITY_PATCH_FILE = "/data/adb/tricky_store/security_patch.txt";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextInputEditText etSystem;
    private TextInputEditText etBoot;
    private TextInputEditText etVendor;
    private TextView tvSystemSecurityPatch;
    private TextView tvTrickyStoreSecurityPatch;
    private View progress;
    private MaterialButton btnSyncSystem;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tricky_store_security_patch);

        Toolbar toolbar = findViewById(R.id.toolbarTrickyStoreSecurityPatch);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tricky Store");
            getSupportActionBar().setSubtitle("安全补丁");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSystemSecurityPatch = findViewById(R.id.tvSystemSecurityPatch);
        tvTrickyStoreSecurityPatch = findViewById(R.id.tvTrickyStoreSecurityPatch);
        etSystem = findViewById(R.id.etSecurityPatchSystem);
        etBoot = findViewById(R.id.etSecurityPatchBoot);
        etVendor = findViewById(R.id.etSecurityPatchVendor);
        progress = findViewById(R.id.progressSecurityPatch);
        btnSyncSystem = findViewById(R.id.btnSecurityPatchSyncSystem);
        btnSave = findViewById(R.id.btnSecurityPatchSave);
        setupDatePickers();

        if (tvSystemSecurityPatch != null) {
            String sp = Build.VERSION.SECURITY_PATCH;
            tvSystemSecurityPatch.setText(sp == null || sp.trim().isEmpty() ? "—" : sp);
        }

        btnSyncSystem.setOnClickListener(v -> syncFieldsWithSystemPatch());
        btnSave.setOnClickListener(v -> saveFile());

        loadFile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tricky_store_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_open_hide_bl_list) {
            startActivity(new Intent(this, TrickyStoreAppListActivity.class));
            return true;
        }
        if (id == R.id.action_set_security_patch) {
            android.widget.Toast.makeText(this, "已在设置安全补丁界面", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setBusy(boolean busy) {
        if (progress != null) {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
        if (btnSyncSystem != null) btnSyncSystem.setEnabled(!busy);
        if (btnSave != null) btnSave.setEnabled(!busy);
        if (etSystem != null) etSystem.setEnabled(!busy);
        if (etBoot != null) etBoot.setEnabled(!busy);
        if (etVendor != null) etVendor.setEnabled(!busy);
    }

    private void loadFile() {
        setBusy(true);
        ioExecutor.execute(() -> {
            String content = readTextFileViaSu(TRICKY_STORE_SECURITY_PATCH_FILE);
            runOnUiThread(() -> {
                setBusy(false);
                if (tvTrickyStoreSecurityPatch != null) {
                    tvTrickyStoreSecurityPatch.setText(content == null || content.trim().isEmpty() ? "—" : content);
                }
                applyParsedPatchToFields(content);
                android.widget.Toast.makeText(this, content == null ? "读取失败" : "已读取", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void saveFile() {
        setBusy(true);
        final String out = buildPatchConfigFromFields();
        ioExecutor.execute(() -> {
            boolean ok = writeTextFileViaSu(TRICKY_STORE_SECURITY_PATCH_FILE, out);
            runOnUiThread(() -> {
                setBusy(false);
                if (ok && tvTrickyStoreSecurityPatch != null) {
                    tvTrickyStoreSecurityPatch.setText(out == null || out.trim().isEmpty() ? "—" : out.trim());
                }
                android.widget.Toast.makeText(this, ok ? "保存成功" : "保存失败", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void syncFieldsWithSystemPatch() {
        String systemPatch = Build.VERSION.SECURITY_PATCH;
        if (systemPatch == null || systemPatch.trim().isEmpty()) {
            android.widget.Toast.makeText(this, "系统安全补丁版本为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String patch = systemPatch.trim();
        if (etSystem != null) etSystem.setText(patch);
        if (etBoot != null) etBoot.setText(patch);
        if (etVendor != null) etVendor.setText(patch);
        android.widget.Toast.makeText(this, "已同步为系统安全补丁日期", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void setupDatePickers() {
        bindDatePicker(etSystem);
        bindDatePicker(etBoot);
        bindDatePicker(etVendor);
    }

    private void bindDatePicker(TextInputEditText target) {
        if (target == null) return;
        target.setFocusable(false);
        target.setFocusableInTouchMode(false);
        target.setCursorVisible(false);
        target.setLongClickable(false);
        target.setKeyListener(null);
        target.setOnClickListener(v -> showComboDatePicker(target));
    }

    private void showComboDatePicker(TextInputEditText target) {
        Calendar calendar = parseDateOrToday(normalizeField(target));
        final int currentYear = calendar.get(Calendar.YEAR);
        final int currentMonth = calendar.get(Calendar.MONTH) + 1;
        final int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding / 2);

        LinearLayout.LayoutParams pickerLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );

        NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setLayoutParams(pickerLp);
        yearPicker.setMinValue(2000);
        yearPicker.setMaxValue(2099);
        yearPicker.setValue(currentYear);
        yearPicker.setWrapSelectorWheel(false);

        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setLayoutParams(pickerLp);
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(currentMonth);
        monthPicker.setWrapSelectorWheel(true);

        NumberPicker dayPicker = new NumberPicker(this);
        dayPicker.setLayoutParams(pickerLp);
        dayPicker.setMinValue(1);
        dayPicker.setWrapSelectorWheel(true);

        Runnable updateDayMax = () -> {
            Calendar tmp = Calendar.getInstance();
            tmp.set(Calendar.YEAR, yearPicker.getValue());
            tmp.set(Calendar.MONTH, monthPicker.getValue() - 1);
            int maxDay = tmp.getActualMaximum(Calendar.DAY_OF_MONTH);
            int oldDay = dayPicker.getValue();
            dayPicker.setMaxValue(maxDay);
            dayPicker.setValue(Math.min(oldDay, maxDay));
        };

        dayPicker.setValue(currentDay);
        updateDayMax.run();

        yearPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateDayMax.run());
        monthPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateDayMax.run());

        container.addView(yearPicker);
        container.addView(monthPicker);
        container.addView(dayPicker);

        new AlertDialog.Builder(this)
                .setTitle("选择日期")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String selected = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            yearPicker.getValue(),
                            monthPicker.getValue(),
                            dayPicker.getValue()
                    );
                    target.setText(selected);
                })
                .show();
    }

    private static Calendar parseDateOrToday(String raw) {
        Calendar calendar = Calendar.getInstance();
        if (TextUtils.isEmpty(raw)) {
            return calendar;
        }
        String value = raw.trim();
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = value.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1;
                int day = Integer.parseInt(parts[2]);
                calendar.set(year, month, day);
            } else if (value.matches("\\d{8}")) {
                int year = Integer.parseInt(value.substring(0, 4));
                int month = Integer.parseInt(value.substring(4, 6)) - 1;
                int day = Integer.parseInt(value.substring(6, 8));
                calendar.set(year, month, day);
            } else if (value.matches("\\d{6}")) {
                int year = Integer.parseInt(value.substring(0, 4));
                int month = Integer.parseInt(value.substring(4, 6)) - 1;
                calendar.set(year, month, 1);
            }
        } catch (Exception ignored) {
            // Keep current date when parsing fails.
        }
        return calendar;
    }

    private void applyParsedPatchToFields(String raw) {
        Map<String, String> kv = parseSecurityPatchConfig(raw);
        if (etSystem != null) etSystem.setText(nullToEmpty(kv.get("system")));
        if (etBoot != null) etBoot.setText(nullToEmpty(kv.get("boot")));
        if (etVendor != null) etVendor.setText(nullToEmpty(kv.get("vendor")));
    }

    private String buildPatchConfigFromFields() {
        String system = normalizeField(etSystem);
        String boot = normalizeField(etBoot);
        String vendor = normalizeField(etVendor);
        if (system.isEmpty() && boot.isEmpty() && vendor.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!system.isEmpty()) sb.append("system=").append(system).append('\n');
        if (!boot.isEmpty()) sb.append("boot=").append(boot).append('\n');
        if (!vendor.isEmpty()) sb.append("vendor=").append(vendor).append('\n');
        return sb.toString().trim();
    }

    private static String normalizeField(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, String> parseSecurityPatchConfig(String raw) {
        Map<String, String> out = new HashMap<>();
        out.put("system", "");
        out.put("boot", "");
        out.put("vendor", "");
        if (raw == null) {
            return out;
        }
        String[] lines = raw.split("\\r?\\n");
        String single = null;
        int kvCount = 0;
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.contains("=")) {
                int idx = s.indexOf('=');
                if (idx <= 0) continue;
                String k = s.substring(0, idx).trim().toLowerCase();
                String v = s.substring(idx + 1).trim();
                if (k.equals("system") || k.equals("boot") || k.equals("vendor")) {
                    out.put(k, v);
                    kvCount++;
                }
            } else if (single == null) {
                single = s;
            }
        }
        if (kvCount == 0 && single != null && !single.isEmpty()) {
            out.put("system", single);
            out.put("boot", single);
            out.put("vendor", single);
        }
        return out;
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
