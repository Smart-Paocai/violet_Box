package com.violet.safe.ui.plugin;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.violet.safe.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HiddenAppListConfigActivity extends AppCompatActivity {

    public static final String EXTRA_MODULE_PACKAGE = "extra_module_package";
    public static final String EXTRA_MODULE_NAME = "extra_module_name";
    public static final String EXTRA_CONFIG_PATH = "extra_config_path";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextView tvConfigMeta;
    private View progress;
    private TextInputEditText etSearch;
    private View btnSave;
    private View btnAddWhitelistTemplate;
    private View btnAddBlacklistTemplate;
    private LinearLayout containerWhitelist;
    private LinearLayout containerBlacklist;
    private LinearLayout containerTemplates;
    private LinearLayout containerScope;
    private TextView tvWhitelistTitle;
    private TextView tvBlacklistTitle;
    private TextView tvWhitelistEmpty;
    private TextView tvBlacklistEmpty;
    private TextView tvTemplatesEmpty;
    private TextView tvScopeEmpty;

    private String moduleName = "";
    private String modulePackage = "";
    private String configPath = "";

    private volatile JSONObject configRoot;
    private volatile JSONObject configTemplates;
    private volatile JSONObject configScope;

    private List<AppRow> whitelistCache = new ArrayList<>();
    private List<AppRow> blacklistCache = new ArrayList<>();
    private List<TemplateRow> templatesCache = new ArrayList<>();
    private List<ScopeRow> scopeCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden_app_list_config);

        modulePackage = safeGetStringExtra(EXTRA_MODULE_PACKAGE);
        moduleName = safeGetStringExtra(EXTRA_MODULE_NAME);
        configPath = safeGetStringExtra(EXTRA_CONFIG_PATH);

        Toolbar toolbar = findViewById(R.id.toolbarHiddenAppList);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("隐藏应用列表");
            getSupportActionBar().setSubtitle(moduleName == null || moduleName.trim().isEmpty() ? "配置" : moduleName.trim());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvConfigMeta = findViewById(R.id.tvHiddenAppListConfigMeta);
        progress = findViewById(R.id.progressHiddenAppList);
        etSearch = findViewById(R.id.etSearchHiddenAppList);
        btnSave = findViewById(R.id.btnSaveHiddenAppListConfig);
        btnAddWhitelistTemplate = findViewById(R.id.btnAddWhitelistTemplate);
        btnAddBlacklistTemplate = findViewById(R.id.btnAddBlacklistTemplate);
        containerWhitelist = findViewById(R.id.containerWhitelistApps);
        containerBlacklist = findViewById(R.id.containerBlacklistApps);
        containerTemplates = findViewById(R.id.containerTemplates);
        containerScope = findViewById(R.id.containerScope);
        tvWhitelistTitle = findViewById(R.id.tvWhitelistTitle);
        tvBlacklistTitle = findViewById(R.id.tvBlacklistTitle);
        tvWhitelistEmpty = findViewById(R.id.tvWhitelistEmpty);
        tvBlacklistEmpty = findViewById(R.id.tvBlacklistEmpty);
        tvTemplatesEmpty = findViewById(R.id.tvTemplatesEmpty);
        tvScopeEmpty = findViewById(R.id.tvScopeEmpty);

        if (tvConfigMeta != null) {
            String meta = "config.json: " + (configPath == null ? "" : configPath);
            if (modulePackage != null && !modulePackage.trim().isEmpty()) {
                meta = meta + "\n模块包名: " + modulePackage.trim();
            }
            tvConfigMeta.setText(meta.trim());
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    renderLists();
                }
            });
        }

        if (btnAddWhitelistTemplate != null) {
            btnAddWhitelistTemplate.setOnClickListener(v -> addTemplate(true));
        }
        if (btnAddBlacklistTemplate != null) {
            btnAddBlacklistTemplate.setOnClickListener(v -> addTemplate(false));
        }
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> persistConfigAsync());
        }

        loadAndRender();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void loadAndRender() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (tvWhitelistEmpty != null) tvWhitelistEmpty.setVisibility(View.GONE);
        if (tvBlacklistEmpty != null) tvBlacklistEmpty.setVisibility(View.GONE);
        if (tvTemplatesEmpty != null) tvTemplatesEmpty.setVisibility(View.GONE);
        if (tvScopeEmpty != null) tvScopeEmpty.setVisibility(View.GONE);
        if (containerWhitelist != null) containerWhitelist.setVisibility(View.GONE);
        if (containerBlacklist != null) containerBlacklist.setVisibility(View.GONE);
        if (containerTemplates != null) containerTemplates.setVisibility(View.GONE);
        if (containerScope != null) containerScope.setVisibility(View.GONE);

        ioExecutor.execute(() -> {
            String raw = readTextFileViaSu(configPath);
            JSONObject root = parseRootOrNull(raw);
            JSONObject templates = ensureObject(root, "templates");
            JSONObject scope = ensureObject(root, "scope");
            configRoot = root;
            configTemplates = templates;
            configScope = scope;

            ParsedLists parsed = parseConfigListsFromTemplates(templates);

            Map<String, AppRow> resolved = resolveAppRows(parsed.allPackages);
            List<AppRow> wl = new ArrayList<>();
            for (String pkg : parsed.whitelistPackages) {
                AppRow r = resolved.get(pkg);
                if (r != null) wl.add(r);
            }
            List<AppRow> bl = new ArrayList<>();
            for (String pkg : parsed.blacklistPackages) {
                AppRow r = resolved.get(pkg);
                if (r != null) bl.add(r);
            }

            List<TemplateRow> templatesRows = parseTemplatesRows(templates);
            List<ScopeRow> scopeRows = parseScopeRows(scope);
            resolveScopeAppRows(scopeRows);

            ComparatorAppRow comparator = new ComparatorAppRow();
            Collections.sort(wl, comparator);
            Collections.sort(bl, comparator);
            Collections.sort(templatesRows, (a, b) -> compareTemplateId(a.templateId, b.templateId));
            Collections.sort(scopeRows, (a, b) -> {
                String an = a == null || a.displayName == null ? "" : a.displayName.toLowerCase(Locale.ROOT);
                String bn = b == null || b.displayName == null ? "" : b.displayName.toLowerCase(Locale.ROOT);
                return an.compareTo(bn);
            });

            runOnUiThread(() -> {
                whitelistCache = wl;
                blacklistCache = bl;
                templatesCache = templatesRows;
                scopeCache = scopeRows;
                if (progress != null) progress.setVisibility(View.GONE);
                if (tvWhitelistTitle != null) tvWhitelistTitle.setText("白名单（" + whitelistCache.size() + "）");
                if (tvBlacklistTitle != null) tvBlacklistTitle.setText("黑名单（" + blacklistCache.size() + "）");

                if ((raw == null || raw.trim().isEmpty()) && tvConfigMeta != null) {
                    tvConfigMeta.setText((tvConfigMeta.getText() == null ? "" : tvConfigMeta.getText().toString())
                            + "\n读取失败：未能通过 su 读取配置内容");
                }
                renderTemplatesAndScope();
                renderLists();
            });
        });
    }

    private void renderLists() {
        String q = etSearch == null || etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        List<AppRow> wl = filterRows(whitelistCache, q);
        List<AppRow> bl = filterRows(blacklistCache, q);

        renderSection(containerWhitelist, tvWhitelistEmpty, wl, whitelistCache.isEmpty() ? "未读取到白名单应用" : "未匹配到白名单应用");
        renderSection(containerBlacklist, tvBlacklistEmpty, bl, blacklistCache.isEmpty() ? "未读取到黑名单应用" : "未匹配到黑名单应用");
    }

    private void renderTemplatesAndScope() {
        renderTemplates();
        renderScope();
    }

    private void renderTemplates() {
        if (containerTemplates == null || tvTemplatesEmpty == null) return;
        containerTemplates.removeAllViews();
        if (templatesCache == null || templatesCache.isEmpty()) {
            tvTemplatesEmpty.setVisibility(View.VISIBLE);
            containerTemplates.setVisibility(View.GONE);
            return;
        }
        tvTemplatesEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TemplateRow tpl : templatesCache) {
            View item = inflater.inflate(R.layout.item_hidden_app_list_template, containerTemplates, false);
            TextView tvTitle = item.findViewById(R.id.tvTemplateTitle);
            TextView tvMeta = item.findViewById(R.id.tvTemplateMeta);
            View btnEdit = item.findViewById(R.id.btnEditTemplate);
            String type = tpl.isWhitelist ? "白名单" : "黑名单";
            if (tvTitle != null) tvTitle.setText("模板 " + tpl.templateId + "（" + type + "）");
            if (tvMeta != null) tvMeta.setText("应用数: " + (tpl.appList == null ? 0 : tpl.appList.size()));
            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> editTemplateApps(tpl.templateId));
            }
            containerTemplates.addView(item);
        }
        containerTemplates.setVisibility(View.VISIBLE);
    }

    private void renderScope() {
        if (containerScope == null || tvScopeEmpty == null) return;
        containerScope.removeAllViews();
        if (scopeCache == null || scopeCache.isEmpty()) {
            tvScopeEmpty.setVisibility(View.VISIBLE);
            containerScope.setVisibility(View.GONE);
            return;
        }
        tvScopeEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ScopeRow row : scopeCache) {
            View item = inflater.inflate(R.layout.item_hidden_app_list_scope_app, containerScope, false);
            android.widget.ImageView iv = item.findViewById(R.id.ivScopeAppIcon);
            TextView tvName = item.findViewById(R.id.tvScopeAppName);
            TextView tvMeta = item.findViewById(R.id.tvScopeAppMeta);
            View btn = item.findViewById(R.id.btnEditScope);
            if (iv != null) iv.setImageDrawable(row.icon);
            if (tvName != null) tvName.setText(row.displayName);
            if (tvMeta != null) {
                String useWhitelist = row.useWhitelist ? "白名单模式" : "黑名单模式";
                String applied = row.applyTemplates == null || row.applyTemplates.isEmpty()
                        ? "未应用模板"
                        : ("已应用模板: " + row.applyTemplates);
                tvMeta.setText(row.packageName + "\n" + useWhitelist + "，" + applied);
            }
            if (btn != null) {
                btn.setOnClickListener(v -> editScopeApplyTemplates(row.packageName));
            }
            containerScope.addView(item);
        }
        containerScope.setVisibility(View.VISIBLE);
    }

    private void renderSection(LinearLayout container, TextView empty, List<AppRow> rows, String emptyText) {
        if (container == null || empty == null) {
            return;
        }
        container.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            empty.setText(emptyText);
            empty.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }
        empty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppRow row : rows) {
            View item = inflater.inflate(R.layout.item_app_simple, container, false);
            android.widget.ImageView iv = item.findViewById(R.id.ivAppIcon);
            TextView tvName = item.findViewById(R.id.tvAppName);
            TextView tvPkg = item.findViewById(R.id.tvPackageName);
            if (iv != null) iv.setImageDrawable(row.icon);
            if (tvName != null) tvName.setText(row.appName);
            if (tvPkg != null) tvPkg.setText(row.packageName);
            container.addView(item);
        }
        container.setVisibility(View.VISIBLE);
    }

    private static List<AppRow> filterRows(List<AppRow> source, String query) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<AppRow> out = new ArrayList<>();
        for (AppRow r : source) {
            if (r == null) continue;
            String name = r.appName == null ? "" : r.appName.toLowerCase(Locale.ROOT);
            String pkg = r.packageName == null ? "" : r.packageName.toLowerCase(Locale.ROOT);
            if (name.contains(needle) || pkg.contains(needle)) {
                out.add(r);
            }
        }
        return out;
    }

    private Map<String, AppRow> resolveAppRows(Set<String> packages) {
        Map<String, AppRow> out = new HashMap<>();
        if (packages == null || packages.isEmpty()) {
            return out;
        }
        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            if (pkg == null || pkg.trim().isEmpty()) continue;
            String p = pkg.trim();
            AppRow row = new AppRow();
            row.packageName = p;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                row.appName = ai.loadLabel(pm).toString();
                row.icon = ai.loadIcon(pm);
            } catch (Throwable ignored) {
                row.appName = p;
                row.icon = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
            }
            out.put(p, row);
        }
        return out;
    }

    private ParsedLists parseConfigLists(String rawJson) {
        ParsedLists out = new ParsedLists();
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return out;
        }
        try {
            JSONObject root = new JSONObject(rawJson);
            JSONObject templates = root.optJSONObject("templates");
            if (templates != null) {
                JSONArray names = templates.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i, "");
                        if (key == null || key.trim().isEmpty()) continue;
                        JSONObject tpl = templates.optJSONObject(key);
                        if (tpl == null) continue;
                        boolean isWhitelist = tpl.optBoolean("isWhitelist", false);
                        JSONArray appList = tpl.optJSONArray("appList");
                        if (appList == null) continue;
                        for (int j = 0; j < appList.length(); j++) {
                            String pkg = appList.optString(j, "");
                            if (pkg == null || pkg.trim().isEmpty()) continue;
                            String p = pkg.trim();
                            out.allPackages.add(p);
                            if (isWhitelist) {
                                out.whitelistPackages.add(p);
                            } else {
                                out.blacklistPackages.add(p);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private JSONObject parseRootOrNull(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(rawJson);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject ensureObject(JSONObject root, String key) {
        if (root == null) return new JSONObject();
        try {
            JSONObject obj = root.optJSONObject(key);
            if (obj != null) return obj;
            obj = new JSONObject();
            root.put(key, obj);
            return obj;
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private ParsedLists parseConfigListsFromTemplates(JSONObject templates) {
        ParsedLists out = new ParsedLists();
        if (templates == null) return out;
        try {
            JSONArray names = templates.names();
            if (names == null) return out;
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, "");
                if (key == null || key.trim().isEmpty()) continue;
                JSONObject tpl = templates.optJSONObject(key);
                if (tpl == null) continue;
                boolean isWhitelist = tpl.optBoolean("isWhitelist", false);
                JSONArray appList = tpl.optJSONArray("appList");
                if (appList == null) continue;
                for (int j = 0; j < appList.length(); j++) {
                    String pkg = appList.optString(j, "");
                    if (pkg == null || pkg.trim().isEmpty()) continue;
                    String p = pkg.trim();
                    out.allPackages.add(p);
                    if (isWhitelist) out.whitelistPackages.add(p);
                    else out.blacklistPackages.add(p);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private List<TemplateRow> parseTemplatesRows(JSONObject templates) {
        List<TemplateRow> out = new ArrayList<>();
        if (templates == null) return out;
        try {
            JSONArray names = templates.names();
            if (names == null) return out;
            for (int i = 0; i < names.length(); i++) {
                String id = names.optString(i, "");
                if (id == null || id.trim().isEmpty()) continue;
                JSONObject tpl = templates.optJSONObject(id);
                if (tpl == null) continue;
                TemplateRow row = new TemplateRow();
                row.templateId = id.trim();
                row.isWhitelist = tpl.optBoolean("isWhitelist", false);
                row.appList = jsonArrayToStringList(tpl.optJSONArray("appList"));
                out.add(row);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private List<ScopeRow> parseScopeRows(JSONObject scope) {
        List<ScopeRow> out = new ArrayList<>();
        if (scope == null) return out;
        try {
            JSONArray names = scope.names();
            if (names == null) return out;
            for (int i = 0; i < names.length(); i++) {
                String pkg = names.optString(i, "");
                if (pkg == null || pkg.trim().isEmpty()) continue;
                JSONObject entry = scope.optJSONObject(pkg);
                if (entry == null) continue;
                ScopeRow row = new ScopeRow();
                row.packageName = pkg.trim();
                row.useWhitelist = entry.optBoolean("useWhitelist", false);
                row.excludeSystemApps = entry.optBoolean("excludeSystemApps", true);
                row.applyTemplates = jsonArrayToStringList(entry.optJSONArray("applyTemplates"));
                out.add(row);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void resolveScopeAppRows(List<ScopeRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        PackageManager pm = getPackageManager();
        for (ScopeRow row : rows) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(row.packageName, 0);
                row.displayName = ai.loadLabel(pm).toString();
                row.icon = ai.loadIcon(pm);
            } catch (Throwable ignored) {
                row.displayName = row.packageName;
                row.icon = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
            }
        }
    }

    private void addTemplate(boolean isWhitelist) {
        JSONObject root = configRoot;
        if (root == null) {
            Toast.makeText(this, "配置未加载，无法新增模板", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject templates = configTemplates;
        if (templates == null) {
            templates = ensureObject(root, "templates");
            configTemplates = templates;
        }
        String newId = String.valueOf(findNextNumericTemplateId(templates));
        try {
            JSONObject tpl = new JSONObject();
            tpl.put("isWhitelist", isWhitelist);
            tpl.put("appList", new JSONArray());
            templates.put(newId, tpl);
        } catch (Throwable ignored) {
            Toast.makeText(this, "新增模板失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "已新增模板 " + newId, Toast.LENGTH_SHORT).show();
        loadAndRender();
    }

    private int findNextNumericTemplateId(JSONObject templates) {
        int max = 0;
        try {
            JSONArray names = templates == null ? null : templates.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String id = names.optString(i, "");
                    if (id == null) continue;
                    String s = id.trim();
                    if (s.matches("\\d+")) {
                        int v = Integer.parseInt(s);
                        if (v > max) max = v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return max + 1;
    }

    private void editTemplateApps(String templateId) {
        JSONObject templates = configTemplates;
        if (templates == null || templateId == null || templateId.trim().isEmpty()) {
            Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject tpl = templates.optJSONObject(templateId);
        if (tpl == null) {
            Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isWhitelist = tpl.optBoolean("isWhitelist", false);
        List<InstalledApp> installed = listInstalledApps();
        if (installed.isEmpty()) {
            Toast.makeText(this, "未读取到应用列表", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> current = jsonArrayToStringList(tpl.optJSONArray("appList"));
        Set<String> currentSet = new HashSet<>(current);
        CharSequence[] labels = new CharSequence[installed.size()];
        boolean[] checked = new boolean[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            InstalledApp a = installed.get(i);
            labels[i] = a.appName + "\n" + a.packageName;
            checked[i] = currentSet.contains(a.packageName);
        }

        String title = "编辑模板 " + templateId + "（" + (isWhitelist ? "白名单" : "黑名单") + "）";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    JSONArray arr = new JSONArray();
                    for (int i = 0; i < installed.size(); i++) {
                        if (checked[i]) {
                            arr.put(installed.get(i).packageName);
                        }
                    }
                    try {
                        tpl.put("appList", arr);
                        templates.put(templateId, tpl);
                        Toast.makeText(this, "已更新模板 " + templateId, Toast.LENGTH_SHORT).show();
                        loadAndRender();
                    } catch (Throwable ignored) {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void editScopeApplyTemplates(String targetPackage) {
        JSONObject scope = configScope;
        JSONObject templates = configTemplates;
        if (scope == null || templates == null || targetPackage == null || targetPackage.trim().isEmpty()) {
            Toast.makeText(this, "配置未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject entry = scope.optJSONObject(targetPackage);
        if (entry == null) {
            Toast.makeText(this, "scope 项不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> templateIds = new ArrayList<>();
        JSONArray names = templates.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String id = names.optString(i, "");
                if (id != null && !id.trim().isEmpty()) templateIds.add(id.trim());
            }
        }
        Collections.sort(templateIds, HiddenAppListConfigActivity::compareTemplateId);
        if (templateIds.isEmpty()) {
            Toast.makeText(this, "请先新增模板", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> current = new HashSet<>(jsonArrayToStringList(entry.optJSONArray("applyTemplates")));
        CharSequence[] labels = new CharSequence[templateIds.size()];
        boolean[] checked = new boolean[templateIds.size()];
        for (int i = 0; i < templateIds.size(); i++) {
            String id = templateIds.get(i);
            JSONObject tpl = templates.optJSONObject(id);
            boolean isWhitelist = tpl != null && tpl.optBoolean("isWhitelist", false);
            int count = tpl == null ? 0 : (tpl.optJSONArray("appList") == null ? 0 : tpl.optJSONArray("appList").length());
            labels[i] = "模板 " + id + "（" + (isWhitelist ? "白名单" : "黑名单") + "，" + count + "）";
            checked[i] = current.contains(id);
        }
        new AlertDialog.Builder(this)
                .setTitle("为 " + targetPackage + " 选择模板")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    JSONArray arr = new JSONArray();
                    for (int i = 0; i < templateIds.size(); i++) {
                        if (checked[i]) arr.put(templateIds.get(i));
                    }
                    try {
                        entry.put("applyTemplates", arr);
                        scope.put(targetPackage, entry);
                        Toast.makeText(this, "已更新 scope", Toast.LENGTH_SHORT).show();
                        loadAndRender();
                    } catch (Throwable ignored) {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void persistConfigAsync() {
        JSONObject root = configRoot;
        if (root == null) {
            Toast.makeText(this, "配置未加载，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        String json = root.toString();
        if (progress != null) progress.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            boolean ok = writeTextFileViaSu(configPath, json);
            runOnUiThread(() -> {
                if (progress != null) progress.setVisibility(View.GONE);
                Toast.makeText(this, ok ? "保存成功" : "保存失败", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private List<InstalledApp> listInstalledApps() {
        List<InstalledApp> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (Throwable t) {
            return out;
        }
        if (apps == null) return out;
        for (ApplicationInfo ai : apps) {
            if (ai == null || ai.packageName == null || ai.packageName.trim().isEmpty()) continue;
            InstalledApp a = new InstalledApp();
            a.packageName = ai.packageName;
            try {
                a.appName = ai.loadLabel(pm).toString();
            } catch (Throwable ignored) {
                a.appName = ai.packageName;
            }
            out.add(a);
        }
        Collections.sort(out, (a, b) -> {
            String an = a == null || a.appName == null ? "" : a.appName.toLowerCase(Locale.ROOT);
            String bn = b == null || b.appName == null ? "" : b.appName.toLowerCase(Locale.ROOT);
            return an.compareTo(bn);
        });
        return out;
    }

    private static List<String> jsonArrayToStringList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    private static int compareTemplateId(String a, String b) {
        String as = a == null ? "" : a.trim();
        String bs = b == null ? "" : b.trim();
        boolean an = as.matches("\\d+");
        boolean bn = bs.matches("\\d+");
        if (an && bn) {
            try {
                return Integer.compare(Integer.parseInt(as), Integer.parseInt(bs));
            } catch (Throwable ignored) {
            }
        }
        return as.compareTo(bs);
    }

    private static class ParsedLists {
        final Set<String> whitelistPackages = new HashSet<>();
        final Set<String> blacklistPackages = new HashSet<>();
        final Set<String> allPackages = new HashSet<>();
    }

    private static class TemplateRow {
        String templateId = "";
        boolean isWhitelist = false;
        List<String> appList = new ArrayList<>();
    }

    private static class ScopeRow {
        String packageName = "";
        String displayName = "";
        android.graphics.drawable.Drawable icon;
        boolean useWhitelist = false;
        boolean excludeSystemApps = true;
        List<String> applyTemplates = new ArrayList<>();
    }

    private static class InstalledApp {
        String appName = "";
        String packageName = "";
    }

    private static class AppRow {
        String appName = "";
        String packageName = "";
        android.graphics.drawable.Drawable icon;
    }

    private static class ComparatorAppRow implements java.util.Comparator<AppRow> {
        @Override
        public int compare(AppRow a, AppRow b) {
            String an = a == null || a.appName == null ? "" : a.appName.toLowerCase(Locale.ROOT);
            String bn = b == null || b.appName == null ? "" : b.appName.toLowerCase(Locale.ROOT);
            return an.compareTo(bn);
        }
    }

    private String safeGetStringExtra(String key) {
        try {
            String v = getIntent() == null ? "" : getIntent().getStringExtra(key);
            return v == null ? "" : v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private List<String[]> buildSuCommands(String cmd) {
        List<String[]> out = new ArrayList<>();
        String c = cmd == null ? "" : cmd;
        out.add(new String[]{"su", "--mount-master", "-c", c});
        out.add(new String[]{"su", "-M", "-c", c});
        out.add(new String[]{"su", "-mm", "-c", c});
        out.add(new String[]{"su", "-c", c});
        out.add(new String[]{"/system/bin/su", "-c", c});
        out.add(new String[]{"/system/xbin/su", "-c", c});
        return out;
    }

    private String readTextFileViaSu(String absolutePath) {
        String path = absolutePath == null ? "" : absolutePath.trim();
        if (path.isEmpty()) return null;
        String cmd = "cat \"" + path.replace("\"", "\\\"") + "\"";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1600, TimeUnit.MILLISECONDS);
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
            } catch (Throwable ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return null;
    }

    private boolean writeTextFileViaSu(String absolutePath, String content) {
        String path = absolutePath == null ? "" : absolutePath.trim();
        if (path.isEmpty()) return false;
        String data = content == null ? "" : content;
        String cmd = "cat > \"" + path.replace("\"", "\\\"") + "\"";
        for (String[] suCmd : buildSuCommands(cmd)) {
            Process process = null;
            try {
                process = new ProcessBuilder(suCmd)
                        .redirectErrorStream(true)
                        .start();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(data);
                    writer.flush();
                }
                boolean finished = process.waitFor(1800, TimeUnit.MILLISECONDS);
                if (finished && process.exitValue() == 0) {
                    return true;
                }
            } catch (Throwable ignored) {
            } finally {
                if (process != null) process.destroy();
            }
        }
        return false;
    }
}

