package com.violet.safe.ui.plugin;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.violet.safe.R;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrickyStoreAppListActivity extends AppCompatActivity {

    private static final String TRICKY_STORE_TARGET_FILE = "/data/adb/tricky_store/target.txt";
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+");
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout containerUserApps;
    private LinearLayout containerSystemApps;
    private View progressUserApps;
    private View progressSystemApps;
    private TextView tvUserAppsEmpty;
    private TextView tvSystemAppsEmpty;
    private TextView tvUserAppsTitle;
    private TextView tvSystemAppsTitle;
    private TextView tvUserAppsToggle;
    private TextView tvSystemAppsToggle;
    private CheckBox cbUserSelectAll;
    private CheckBox cbSystemSelectAll;
    private com.google.android.material.textfield.TextInputEditText etSearchApps;
    private boolean userExpanded = false;
    private boolean systemExpanded = false;
    private boolean userLoaded = false;
    private boolean systemLoaded = false;
    private final Set<String> selectedUserPackages = new HashSet<>();
    private final Set<String> selectedSystemPackages = new HashSet<>();
    private List<AppItem> userAppsCache = new ArrayList<>();
    private List<AppItem> systemAppsCache = new ArrayList<>();
    private Set<String> targetConfigPackages = new HashSet<>();
    private final Set<String> selectedPackagesMaster = new HashSet<>();
    private boolean targetConfigLoaded = false;
    private boolean suppressSelectAllCallback = false;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tricky_store_app_list);

        Toolbar toolbar = findViewById(R.id.toolbarTrickyStoreApps);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tricky Store");
            getSupportActionBar().setSubtitle("隐藏BL列表");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        containerUserApps = findViewById(R.id.containerUserApps);
        containerSystemApps = findViewById(R.id.containerSystemApps);
        progressUserApps = findViewById(R.id.progressUserApps);
        progressSystemApps = findViewById(R.id.progressSystemApps);
        tvUserAppsEmpty = findViewById(R.id.tvUserAppsEmpty);
        tvSystemAppsEmpty = findViewById(R.id.tvSystemAppsEmpty);
        tvUserAppsTitle = findViewById(R.id.tvUserAppsTitle);
        tvSystemAppsTitle = findViewById(R.id.tvSystemAppsTitle);
        tvUserAppsToggle = findViewById(R.id.tvUserAppsToggle);
        tvSystemAppsToggle = findViewById(R.id.tvSystemAppsToggle);
        cbUserSelectAll = findViewById(R.id.cbUserSelectAll);
        cbSystemSelectAll = findViewById(R.id.cbSystemSelectAll);
        etSearchApps = findViewById(R.id.etSearchApps);

        View headerUser = findViewById(R.id.layoutUserAppsHeader);
        View headerSystem = findViewById(R.id.layoutSystemAppsHeader);
        headerUser.setOnClickListener(v -> toggleUserSection());
        headerSystem.setOnClickListener(v -> toggleSystemSection());
        cbUserSelectAll.setOnClickListener(v -> onUserSelectAllChanged(cbUserSelectAll.isChecked()));
        cbSystemSelectAll.setOnClickListener(v -> onSystemSelectAllChanged(cbSystemSelectAll.isChecked()));
        if (etSearchApps != null) {
            etSearchApps.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    currentSearchQuery = s == null ? "" : s.toString().trim();
                    renderCurrentSections();
                }
            });
        }

        // 默认展开并加载用户应用
        toggleUserSection();
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
            android.widget.Toast.makeText(this, "已在隐藏BL列表界面", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_set_security_patch) {
            startActivity(new Intent(this, TrickyStoreSecurityPatchActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void toggleUserSection() {
        userExpanded = !userExpanded;
        tvUserAppsToggle.setText(userExpanded ? "收起" : "展开");
        if (!userExpanded) {
            containerUserApps.setVisibility(View.GONE);
            progressUserApps.setVisibility(View.GONE);
            tvUserAppsEmpty.setVisibility(View.GONE);
            return;
        }
        if (userLoaded) {
            containerUserApps.setVisibility(View.VISIBLE);
            return;
        }
        loadUserApps();
    }

    private void toggleSystemSection() {
        systemExpanded = !systemExpanded;
        tvSystemAppsToggle.setText(systemExpanded ? "收起" : "展开");
        if (!systemExpanded) {
            containerSystemApps.setVisibility(View.GONE);
            progressSystemApps.setVisibility(View.GONE);
            tvSystemAppsEmpty.setVisibility(View.GONE);
            return;
        }
        if (systemLoaded) {
            containerSystemApps.setVisibility(View.VISIBLE);
            return;
        }
        loadSystemApps();
    }

    private void loadUserApps() {
        progressUserApps.setVisibility(View.VISIBLE);
        containerUserApps.setVisibility(View.GONE);
        tvUserAppsEmpty.setVisibility(View.GONE);
        ioExecutor.execute(() -> {
            ensureTargetConfigLoaded();
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppItem> userApps = new ArrayList<>();
            for (ApplicationInfo app : installedApps) {
                if (!isSystemApp(app)) {
                    String appName = app.loadLabel(packageManager).toString();
                    userApps.add(new AppItem(appName, app.packageName, app.loadIcon(packageManager)));
                    if (selectedPackagesMaster.contains(app.packageName)) {
                        selectedUserPackages.add(app.packageName);
                    }
                }
            }
            Comparator<AppItem> comparator = Comparator.comparing(a -> a.appName.toLowerCase(Locale.ROOT));
            Collections.sort(userApps, comparator);
            runOnUiThread(() -> {
                userAppsCache = userApps;
                userLoaded = true;
                progressUserApps.setVisibility(View.GONE);
                if (!userExpanded) {
                    return;
                }
                renderCurrentSections();
            });
        });
    }

    private void loadSystemApps() {
        progressSystemApps.setVisibility(View.VISIBLE);
        containerSystemApps.setVisibility(View.GONE);
        tvSystemAppsEmpty.setVisibility(View.GONE);
        ioExecutor.execute(() -> {
            ensureTargetConfigLoaded();
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppItem> systemApps = new ArrayList<>();
            for (ApplicationInfo app : installedApps) {
                if (isSystemApp(app)) {
                    String appName = app.loadLabel(packageManager).toString();
                    systemApps.add(new AppItem(appName, app.packageName, app.loadIcon(packageManager)));
                    if (selectedPackagesMaster.contains(app.packageName)) {
                        selectedSystemPackages.add(app.packageName);
                    }
                }
            }
            Comparator<AppItem> comparator = Comparator.comparing(a -> a.appName.toLowerCase(Locale.ROOT));
            Collections.sort(systemApps, comparator);
            runOnUiThread(() -> {
                systemAppsCache = systemApps;
                systemLoaded = true;
                progressSystemApps.setVisibility(View.GONE);
                if (!systemExpanded) {
                    return;
                }
                renderCurrentSections();
            });
        });
    }

    private void renderAppRows(LinearLayout container, List<AppItem> apps, Set<String> selectedPackages) {
        container.removeAllViews();
        List<AppItem> sortedApps = new ArrayList<>(apps);
        Collections.sort(sortedApps, (a, b) -> {
            boolean aChecked = selectedPackages.contains(a.packageName);
            boolean bChecked = selectedPackages.contains(b.packageName);
            if (aChecked != bChecked) {
                return aChecked ? -1 : 1;
            }
            return a.appName.toLowerCase(Locale.ROOT).compareTo(b.appName.toLowerCase(Locale.ROOT));
        });

        for (AppItem row : sortedApps) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_tricky_store_app, container, false);
            CheckBox checkBox = itemView.findViewById(R.id.cbAppSelect);
            TextView tvName = itemView.findViewById(R.id.tvAppName);
            TextView tvPackage = itemView.findViewById(R.id.tvPackageName);
            android.widget.ImageView ivIcon = itemView.findViewById(R.id.ivAppIcon);

            ivIcon.setImageDrawable(row.icon);
            tvName.setText(row.appName);
            tvPackage.setText(row.packageName);
            checkBox.setChecked(selectedPackages.contains(row.packageName));

            View.OnClickListener toggleListener = v -> {
                if (selectedPackages.contains(row.packageName)) {
                    selectedPackages.remove(row.packageName);
                    selectedPackagesMaster.remove(row.packageName);
                } else {
                    selectedPackages.add(row.packageName);
                    selectedPackagesMaster.add(row.packageName);
                }
                if (container == containerUserApps) {
                    renderCurrentSections();
                } else if (container == containerSystemApps) {
                    renderCurrentSections();
                }
                syncSelectAllCheckboxes();
                persistTargetConfigAsync();
            };
            checkBox.setOnClickListener(toggleListener);
            itemView.setOnClickListener(toggleListener);

            container.addView(itemView);
        }
    }

    private void onUserSelectAllChanged(boolean checked) {
        if (suppressSelectAllCallback) {
            return;
        }
        selectedUserPackages.clear();
        for (AppItem item : userAppsCache) {
            selectedPackagesMaster.remove(item.packageName);
        }
        if (checked) {
            for (AppItem item : userAppsCache) {
                selectedUserPackages.add(item.packageName);
                selectedPackagesMaster.add(item.packageName);
            }
        }
        if (userLoaded && userExpanded) {
            renderCurrentSections();
        }
        syncSelectAllCheckboxes();
        persistTargetConfigAsync();
    }

    private void onSystemSelectAllChanged(boolean checked) {
        if (suppressSelectAllCallback) {
            return;
        }
        selectedSystemPackages.clear();
        for (AppItem item : systemAppsCache) {
            selectedPackagesMaster.remove(item.packageName);
        }
        if (checked) {
            for (AppItem item : systemAppsCache) {
                selectedSystemPackages.add(item.packageName);
                selectedPackagesMaster.add(item.packageName);
            }
        }
        if (systemLoaded && systemExpanded) {
            renderCurrentSections();
        }
        syncSelectAllCheckboxes();
        persistTargetConfigAsync();
    }

    private void syncSelectAllCheckboxes() {
        suppressSelectAllCallback = true;
        cbUserSelectAll.setChecked(!userAppsCache.isEmpty() && selectedUserPackages.size() == userAppsCache.size());
        cbSystemSelectAll.setChecked(!systemAppsCache.isEmpty() && selectedSystemPackages.size() == systemAppsCache.size());
        suppressSelectAllCallback = false;
    }

    private void renderCurrentSections() {
        List<AppItem> filteredUserApps = getFilteredApps(userAppsCache, currentSearchQuery);
        List<AppItem> filteredSystemApps = getFilteredApps(systemAppsCache, currentSearchQuery);

        tvUserAppsTitle.setText("用户应用（" + filteredUserApps.size() + "/" + userAppsCache.size() + "）");
        tvSystemAppsTitle.setText("系统应用（" + filteredSystemApps.size() + "/" + systemAppsCache.size() + "）");

        if (userExpanded && userLoaded) {
            if (filteredUserApps.isEmpty()) {
                tvUserAppsEmpty.setText(userAppsCache.isEmpty() ? "未读取到用户应用" : "未匹配到用户应用");
                tvUserAppsEmpty.setVisibility(View.VISIBLE);
                containerUserApps.setVisibility(View.GONE);
            } else {
                tvUserAppsEmpty.setVisibility(View.GONE);
                renderAppRows(containerUserApps, filteredUserApps, selectedUserPackages);
                containerUserApps.setVisibility(View.VISIBLE);
            }
        }

        if (systemExpanded && systemLoaded) {
            if (filteredSystemApps.isEmpty()) {
                tvSystemAppsEmpty.setText(systemAppsCache.isEmpty() ? "未读取到系统应用" : "未匹配到系统应用");
                tvSystemAppsEmpty.setVisibility(View.VISIBLE);
                containerSystemApps.setVisibility(View.GONE);
            } else {
                tvSystemAppsEmpty.setVisibility(View.GONE);
                renderAppRows(containerSystemApps, filteredSystemApps, selectedSystemPackages);
                containerSystemApps.setVisibility(View.VISIBLE);
            }
        }

        syncSelectAllCheckboxes();
    }

    private static List<AppItem> getFilteredApps(List<AppItem> source, String query) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<AppItem> out = new ArrayList<>();
        for (AppItem item : source) {
            if (item == null) continue;
            String appName = item.appName == null ? "" : item.appName.toLowerCase(Locale.ROOT);
            String packageName = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
            if (appName.contains(needle) || packageName.contains(needle)) {
                out.add(item);
            }
        }
        return out;
    }

    private boolean isSystemApp(ApplicationInfo appInfo) {
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private void ensureTargetConfigLoaded() {
        if (targetConfigLoaded) {
            return;
        }
        targetConfigPackages = readTargetPackagesViaSu();
        selectedPackagesMaster.clear();
        selectedPackagesMaster.addAll(targetConfigPackages);
        targetConfigLoaded = true;
    }

    private void persistTargetConfigAsync() {
        Set<String> snapshot = new HashSet<>(selectedPackagesMaster);
        ioExecutor.execute(() -> {
            boolean success = writeTargetPackagesViaSu(snapshot);
            runOnUiThread(() -> android.widget.Toast.makeText(
                    this,
                    success ? "修改成功" : "修改失败",
                    android.widget.Toast.LENGTH_SHORT
            ).show());
        });
    }

    private boolean writeTargetPackagesViaSu(Set<String> packages) {
        List<String> sorted = new ArrayList<>(packages);
        Collections.sort(sorted);
        StringBuilder content = new StringBuilder();
        for (String pkg : sorted) {
            if (PACKAGE_NAME_PATTERN.matcher(pkg).matches()) {
                content.append(pkg).append('\n');
            }
        }
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "cat > \"" + TRICKY_STORE_TARGET_FILE + "\"")
                        .redirectErrorStream(true)
                        .start();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(content.toString());
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


    private Set<String> readTargetPackagesViaSu() {
        Set<String> packages = new HashSet<>();
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "cat \"" + TRICKY_STORE_TARGET_FILE + "\"")
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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = PACKAGE_NAME_PATTERN.matcher(line);
                        while (matcher.find()) {
                            packages.add(matcher.group());
                        }
                    }
                }
                return packages;
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return packages;
    }

    private static class AppItem {
        final String appName;
        final String packageName;
        final android.graphics.drawable.Drawable icon;

        AppItem(String appName, String packageName, android.graphics.drawable.Drawable icon) {
            this.appName = appName;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
