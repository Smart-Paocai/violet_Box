package com.violet.safe.ui.plugin;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.violet.safe.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HiddenAppListConfigActivity extends AppCompatActivity {

    public static final String EXTRA_MODULE_PACKAGE = "extra_module_package";
    public static final String EXTRA_MODULE_NAME = "extra_module_name";
    public static final String EXTRA_CONFIG_PATH = "extra_config_path";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private View progress;
    private View btnAddTemplate;
    private LinearLayout containerTemplates;
    private LinearLayout containerScope;
    private TextView tvTemplatesEmpty;
    private TextView tvScopeEmpty;
    private CheckBox cbFilterSystemScopeApps;

    private String moduleName = "";
    private String configPath = "";

    private volatile JSONObject configRoot;
    private volatile JSONObject configTemplates;
    private volatile JSONObject configScope;
    private boolean filterSystemScopeApps = true;

    private List<TemplateRow> templatesCache = new ArrayList<>();
    private List<ScopeRow> scopeCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden_app_list_config);

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

        progress = findViewById(R.id.progressHiddenAppList);
        btnAddTemplate = findViewById(R.id.btnAddTemplate);
        containerTemplates = findViewById(R.id.containerTemplates);
        containerScope = findViewById(R.id.containerScope);
        tvTemplatesEmpty = findViewById(R.id.tvTemplatesEmpty);
        tvScopeEmpty = findViewById(R.id.tvScopeEmpty);
        cbFilterSystemScopeApps = findViewById(R.id.cbFilterSystemScopeApps);

        if (btnAddTemplate != null) {
            btnAddTemplate.setOnClickListener(v -> showTemplateTypePicker());
        }
        if (cbFilterSystemScopeApps != null) {
            cbFilterSystemScopeApps.setChecked(filterSystemScopeApps);
            cbFilterSystemScopeApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
                filterSystemScopeApps = isChecked;
                renderScope();
            });
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
        if (tvTemplatesEmpty != null) tvTemplatesEmpty.setVisibility(View.GONE);
        if (tvScopeEmpty != null) tvScopeEmpty.setVisibility(View.GONE);
        if (containerTemplates != null) containerTemplates.setVisibility(View.GONE);
        if (containerScope != null) containerScope.setVisibility(View.GONE);

        ioExecutor.execute(() -> {
            String raw = readTextFileViaSu(configPath);
            JSONObject root = parseRootOrNull(raw);
            if (root == null) {
                root = new JSONObject();
                try {
                    root.put("configVersion", 1);
                    root.put("templates", new JSONObject());
                    root.put("scope", new JSONObject());
                } catch (Throwable ignored) {
                }
            }
            JSONObject templates = ensureObject(root, "templates");
            JSONObject scope = ensureObject(root, "scope");
            configRoot = root;
            configTemplates = templates;
            configScope = scope;

            List<TemplateRow> templatesRows = parseTemplatesRows(templates);
            List<ScopeRow> scopeRows = buildScopeRows(scope);

            Collections.sort(templatesRows, (a, b) -> compareTemplateId(a.templateId, b.templateId));
            Collections.sort(scopeRows, (a, b) -> {
                String an = a == null || a.displayName == null ? "" : a.displayName.toLowerCase(Locale.ROOT);
                String bn = b == null || b.displayName == null ? "" : b.displayName.toLowerCase(Locale.ROOT);
                return an.compareTo(bn);
            });

            runOnUiThread(() -> {
                templatesCache = templatesRows;
                scopeCache = scopeRows;
                if (progress != null) progress.setVisibility(View.GONE);

                rebuildCachesAndRender();
            });
        });
    }

    private void rebuildCachesAndRender() {
        JSONObject templates = configTemplates;
        JSONObject scope = configScope;
        List<TemplateRow> templatesRows = parseTemplatesRows(templates);
        List<ScopeRow> scopeRows = buildScopeRows(scope);
        Collections.sort(templatesRows, (a, b) -> compareTemplateId(a.templateId, b.templateId));
        Collections.sort(scopeRows, (a, b) -> {
            String an = a == null || a.displayName == null ? "" : a.displayName.toLowerCase(Locale.ROOT);
            String bn = b == null || b.displayName == null ? "" : b.displayName.toLowerCase(Locale.ROOT);
            return an.compareTo(bn);
        });
        templatesCache = templatesRows;
        scopeCache = scopeRows;
        renderTemplatesAndScope();
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

        List<TemplateRow> whitelist = new ArrayList<>();
        List<TemplateRow> blacklist = new ArrayList<>();
        for (TemplateRow tpl : templatesCache) {
            if (tpl != null && tpl.isWhitelist) {
                whitelist.add(tpl);
            } else if (tpl != null) {
                blacklist.add(tpl);
            }
        }

        if (!whitelist.isEmpty()) {
            addTemplateSectionTitle("白名单模板", false);
            for (TemplateRow tpl : whitelist) {
                addTemplateRowItem(tpl);
            }
        }
        if (!blacklist.isEmpty()) {
            addTemplateSectionTitle("黑名单模板", !whitelist.isEmpty());
            for (TemplateRow tpl : blacklist) {
                addTemplateRowItem(tpl);
            }
        }
        containerTemplates.setVisibility(View.VISIBLE);
    }

    private void addTemplateSectionTitle(String title, boolean withTopMargin) {
        if (containerTemplates == null) return;
        TextView tv = new TextView(this);
        tv.setText(title == null ? "" : title);
        tv.setTextSize(13f);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(getResources().getColor(R.color.explore_slate_500, getTheme()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int top = withTopMargin ? dp(8) : 0;
        lp.setMargins(0, top, 0, dp(6));
        tv.setLayoutParams(lp);
        containerTemplates.addView(tv);
    }

    private void addTemplateRowItem(TemplateRow tpl) {
        if (tpl == null || containerTemplates == null) return;
        LayoutInflater inflater = LayoutInflater.from(this);
        View item = inflater.inflate(R.layout.item_hidden_app_list_template, containerTemplates, false);
        TextView tvTitle = item.findViewById(R.id.tvTemplateTitle);
        TextView tvMeta = item.findViewById(R.id.tvTemplateMeta);
        View btnEdit = item.findViewById(R.id.btnEditTemplate);
        View btnDelete = item.findViewById(R.id.btnDeleteTemplate);
        if (tvTitle != null) {
            tvTitle.setText(tpl.templateId);
            tvTitle.setOnClickListener(v -> showTemplateRenameDialog(tpl));
        }
        if (tvMeta != null) tvMeta.setText("应用数: " + (tpl.appList == null ? 0 : tpl.appList.size()));
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> editTemplateApps(tpl.templateId, false));
        }
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> confirmDeleteTemplate(tpl.templateId));
        }
        containerTemplates.addView(item);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void showTemplateRenameDialog(TemplateRow tpl) {
        if (tpl == null || tpl.templateId == null || tpl.templateId.trim().isEmpty()) return;
        String oldId = tpl.templateId.trim();
        showTemplateNameInputDialog(
                "重命名模板",
                oldId,
                "请输入新的模板名称",
                "保存",
                newId -> {
                    if (oldId.equals(newId)) return true;
                    return renameTemplate(oldId, newId);
                }
        );
    }

    private boolean renameTemplate(String oldId, String newId) {
        JSONObject templates = configTemplates;
        JSONObject scope = configScope;
        if (templates == null || oldId == null || newId == null) return false;
        String from = oldId.trim();
        String to = newId.trim();
        if (from.isEmpty() || to.isEmpty()) return false;
        JSONObject tplObj = templates.optJSONObject(from);
        if (tplObj == null) {
            Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (templates.optJSONObject(to) != null) {
            Toast.makeText(this, "模板名称已存在，请更换", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            templates.remove(from);
            templates.put(to, tplObj);
            if (scope != null) {
                JSONArray names = scope.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String pkg = names.optString(i, "");
                        if (pkg == null || pkg.trim().isEmpty()) continue;
                        JSONObject entry = scope.optJSONObject(pkg);
                        if (entry == null) continue;
                        JSONArray oldArr = entry.optJSONArray("applyTemplates");
                        if (oldArr == null) continue;
                        JSONArray newArr = new JSONArray();
                        for (int j = 0; j < oldArr.length(); j++) {
                            String v = oldArr.optString(j, "");
                            if (v == null || v.trim().isEmpty()) continue;
                            String id = v.trim();
                            newArr.put(from.equals(id) ? to : id);
                        }
                        entry.put("applyTemplates", newArr);
                        scope.put(pkg, entry);
                    }
                }
            }
            persistAndRender("已重命名模板 " + from + " -> " + to);
            return true;
        } catch (Throwable ignored) {
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void confirmDeleteTemplate(String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除模板")
                .setMessage("确定删除模板 " + templateId + " 吗？\n将同步移除 scope 中对此模板的引用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteTemplate(templateId))
                .show();
    }

    private void deleteTemplate(String templateId) {
        JSONObject templates = configTemplates;
        JSONObject scope = configScope;
        if (templates == null || templateId == null || templateId.trim().isEmpty()) {
            Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        String id = templateId.trim();
        if (templates.optJSONObject(id) == null) {
            Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            templates.remove(id);
            if (scope != null) {
                JSONArray names = scope.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String pkg = names.optString(i, "");
                        if (pkg == null || pkg.trim().isEmpty()) continue;
                        JSONObject entry = scope.optJSONObject(pkg);
                        if (entry == null) continue;
                        JSONArray oldArr = entry.optJSONArray("applyTemplates");
                        if (oldArr == null) continue;
                        JSONArray newArr = new JSONArray();
                        for (int j = 0; j < oldArr.length(); j++) {
                            String v = oldArr.optString(j, "");
                            if (v == null || v.trim().isEmpty()) continue;
                            if (!id.equals(v.trim())) newArr.put(v.trim());
                        }
                        entry.put("applyTemplates", newArr);
                        scope.put(pkg, entry);
                    }
                }
            }
            persistAndRender("已删除模板 " + id);
        } catch (Throwable ignored) {
            Toast.makeText(this, "删除模板失败", Toast.LENGTH_SHORT).show();
        }
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
            if (filterSystemScopeApps && row.isSystemApp) continue;
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
        if (containerScope.getChildCount() == 0) {
            tvScopeEmpty.setText("已过滤系统应用，暂无可显示项");
            tvScopeEmpty.setVisibility(View.VISIBLE);
            containerScope.setVisibility(View.GONE);
            return;
        }
        containerScope.setVisibility(View.VISIBLE);
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

    private List<ScopeRow> buildScopeRows(JSONObject scope) {
        List<ScopeRow> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps;
        try {
            installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (Throwable ignored) {
            installedApps = new ArrayList<>();
        }
        for (ApplicationInfo ai : installedApps) {
            if (ai == null || ai.packageName == null || ai.packageName.trim().isEmpty()) continue;
            if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) continue;
            String pkg = ai.packageName.trim();
            JSONObject entry = scope == null ? null : scope.optJSONObject(pkg);
            try {
                ScopeRow row = new ScopeRow();
                row.packageName = pkg;
                row.displayName = ai.loadLabel(pm).toString();
                row.icon = ai.loadIcon(pm);
                row.isInstalled = true;
                row.isSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (entry != null) {
                    row.useWhitelist = entry.optBoolean("useWhitelist", false);
                    row.excludeSystemApps = entry.optBoolean("excludeSystemApps", true);
                    row.applyTemplates = jsonArrayToStringList(entry.optJSONArray("applyTemplates"));
                }
                out.add(row);
            } catch (Throwable ignored) {
                // ignore one broken package entry
            }
        }
        return out;
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
        showCreateTemplateDialog(isWhitelist, templates);
    }

    private void showTemplateTypePicker() {
        CharSequence[] types = new CharSequence[]{
                "白名单（希望目标应用看到的）",
                "黑名单（希望目标应用看不到的）"
        };
        new AlertDialog.Builder(this)
                .setTitle("选择模板类型")
                .setItems(types, (dialog, which) -> addTemplate(which == 0))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateTemplateDialog(boolean isWhitelist, JSONObject templates) {
        String title = isWhitelist ? "新增白名单模板" : "新增黑名单模板";
        showTemplateNameInputDialog(
                title,
                "",
                "请输入模板名称",
                "下一步",
                templateId -> {
                    if (templates.optJSONObject(templateId) != null) {
                        Toast.makeText(this, "模板名称已存在，请更换", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    showCreateTemplateAppPicker(templateId, isWhitelist, false);
                    return true;
                }
        );
    }

    private void showCreateTemplateAppPicker(String templateId, boolean isWhitelist, boolean includeSystemApps) {
        JSONObject templates = configTemplates;
        if (templates == null) {
            Toast.makeText(this, "配置未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        showTemplateAppPickerDialog(
                templateId,
                isWhitelist,
                includeSystemApps,
                new ArrayList<>(),
                "创建",
                selectedPackages -> {
                    JSONArray arr = new JSONArray();
                    for (String pkg : selectedPackages) arr.put(pkg);
                    try {
                        JSONObject tpl = new JSONObject();
                        tpl.put("isWhitelist", isWhitelist);
                        tpl.put("appList", arr);
                        templates.put(templateId, tpl);
                        persistAndRender("已新增模板 " + templateId);
                    } catch (Throwable ignored) {
                        Toast.makeText(this, "新增模板失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showTemplateNameInputDialog(
            String title,
            String initialValue,
            String hint,
            String positiveText,
            TemplateNameSubmit submit
    ) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(hint == null ? "" : hint);
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        if (initialValue != null && !initialValue.trim().isEmpty()) {
            input.setText(initialValue.trim());
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
        }
        inputLayout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        inputLayout.setPadding(dp(8), dp(6), dp(8), 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title == null ? "" : title)
                .setView(inputLayout)
                .setNegativeButton("取消", null)
                .setPositiveButton(positiveText == null ? "确定" : positiveText, null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (value.isEmpty()) {
                    inputLayout.setError("模板名称不能为空");
                    return;
                }
                inputLayout.setError(null);
                boolean ok = submit != null && submit.onSubmit(value);
                if (ok) dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void editTemplateApps(String templateId, boolean includeSystemApps) {
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
        List<String> current = jsonArrayToStringList(tpl.optJSONArray("appList"));
        showTemplateAppPickerDialog(
                templateId,
                isWhitelist,
                includeSystemApps,
                current,
                "确定",
                selectedPackages -> {
                    JSONArray arr = new JSONArray();
                    for (String pkg : selectedPackages) arr.put(pkg);
                    try {
                        tpl.put("appList", arr);
                        templates.put(templateId, tpl);
                        persistAndRender("已更新模板 " + templateId);
                    } catch (Throwable ignored) {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showTemplateAppPickerDialog(
            String templateId,
            boolean isWhitelist,
            boolean includeSystemApps,
            List<String> presetSelectedPackages,
            String positiveText,
            AppSelectionCallback onSelected
    ) {
        List<InstalledApp> installed = listInstalledApps(includeSystemApps);
        if (installed.isEmpty()) {
            Toast.makeText(this, "未读取到应用列表", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> selectedPackages = new HashSet<>();
        if (presetSelectedPackages != null) {
            for (String pkg : presetSelectedPackages) {
                if (pkg != null && !pkg.trim().isEmpty()) selectedPackages.add(pkg.trim());
            }
        }

        String mode = includeSystemApps ? "全部应用" : "仅用户应用";
        String title = "编辑模板 " + templateId + "（" + (isWhitelist ? "白名单" : "黑名单") + "，" + mode + "）";

        List<InstalledApp> filtered = new ArrayList<>(installed);
        EditText etSearch = new EditText(this);
        etSearch.setHint("搜索应用名称或包名");
        etSearch.setSingleLine(true);
        int densityPad = (int) (12 * getResources().getDisplayMetrics().density);
        etSearch.setPadding(densityPad, densityPad / 2, densityPad, densityPad / 2);
        CheckBox cbSelectAll = new CheckBox(this);
        cbSelectAll.setText("全选（当前筛选）");

        Runnable syncSelectAllState = () -> {
            if (filtered.isEmpty()) {
                cbSelectAll.setChecked(false);
                return;
            }
            for (InstalledApp app : filtered) {
                if (!selectedPackages.contains(app.packageName)) {
                    cbSelectAll.setChecked(false);
                    return;
                }
            }
            cbSelectAll.setChecked(true);
        };

        ListView listView = new ListView(this);
        listView.setDividerHeight(0);
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filtered.size();
            }

            @Override
            public Object getItem(int position) {
                return filtered.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView == null
                        ? LayoutInflater.from(HiddenAppListConfigActivity.this).inflate(R.layout.item_tricky_store_app, parent, false)
                        : convertView;
                CheckBox cb = view.findViewById(R.id.cbAppSelect);
                ImageView ivIcon = view.findViewById(R.id.ivAppIcon);
                TextView tvName = view.findViewById(R.id.tvAppName);
                TextView tvPkg = view.findViewById(R.id.tvPackageName);
                InstalledApp app = filtered.get(position);
                if (ivIcon != null) ivIcon.setImageDrawable(app.icon);
                if (tvName != null) tvName.setText(app.appName);
                if (tvPkg != null) tvPkg.setText(app.packageName);
                if (cb != null) cb.setChecked(selectedPackages.contains(app.packageName));
                View.OnClickListener toggle = v -> {
                    if (selectedPackages.contains(app.packageName)) {
                        selectedPackages.remove(app.packageName);
                    } else {
                        selectedPackages.add(app.packageName);
                    }
                    notifyDataSetChanged();
                    syncSelectAllState.run();
                };
                view.setOnClickListener(toggle);
                if (cb != null) cb.setOnClickListener(toggle);
                return view;
            }
        };
        listView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String needle = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                if (needle.isEmpty()) {
                    filtered.addAll(installed);
                } else {
                    for (InstalledApp app : installed) {
                        String name = app.appName == null ? "" : app.appName.toLowerCase(Locale.ROOT);
                        String pkg = app.packageName == null ? "" : app.packageName.toLowerCase(Locale.ROOT);
                        if (name.contains(needle) || pkg.contains(needle)) {
                            filtered.add(app);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
                syncSelectAllState.run();
            }
        });

        cbSelectAll.setOnClickListener(v -> {
            boolean checked = cbSelectAll.isChecked();
            for (InstalledApp app : filtered) {
                if (checked) {
                    selectedPackages.add(app.packageName);
                } else {
                    selectedPackages.remove(app.packageName);
                }
            }
            adapter.notifyDataSetChanged();
            syncSelectAllState.run();
        });

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        int outerPad = (int) (8 * getResources().getDisplayMetrics().density);
        dialogContent.setPadding(outerPad, outerPad, outerPad, outerPad);
        dialogContent.addView(etSearch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        dialogContent.addView(cbSelectAll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        dialogContent.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        syncSelectAllState.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogContent)
                .setNegativeButton("取消", null)
                .setPositiveButton(positiveText, (dialog, which) -> {
                    List<String> selected = new ArrayList<>(selectedPackages);
                    Collections.sort(selected);
                    onSelected.onSelected(selected);
                });
        if (!includeSystemApps) {
            builder.setNeutralButton("加载系统应用", (dialog, which) -> showTemplateAppPickerDialog(
                    templateId,
                    isWhitelist,
                    true,
                    new ArrayList<>(selectedPackages),
                    positiveText,
                    onSelected
            ));
        }
        builder.show();
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
            entry = new JSONObject();
            try {
                entry.put("useWhitelist", false);
                entry.put("excludeSystemApps", true);
                entry.put("applyTemplates", new JSONArray());
                scope.put(targetPackage, entry);
            } catch (Throwable ignored) {
                Toast.makeText(this, "scope 项创建失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        final JSONObject finalEntry = entry;
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
        Set<String> current = new HashSet<>(jsonArrayToStringList(finalEntry.optJSONArray("applyTemplates")));
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
                        finalEntry.put("applyTemplates", arr);
                        scope.put(targetPackage, finalEntry);
                        persistAndRender("已更新 scope");
                    } catch (Throwable ignored) {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void persistAndRender(String successHint) {
        rebuildCachesAndRender();
        persistConfigAsync(successHint);
    }

    private void persistConfigAsync(String successHint) {
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
                String msg = ok
                        ? ((successHint == null || successHint.trim().isEmpty()) ? "已自动保存" : (successHint + "，已自动保存"))
                        : "自动保存失败";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private List<InstalledApp> listInstalledApps(boolean includeSystemApps) {
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
            if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) continue;
            boolean isSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!includeSystemApps) {
                if (isSystemApp) continue;
            }
            InstalledApp a = new InstalledApp();
            a.packageName = ai.packageName;
            try {
                a.appName = ai.loadLabel(pm).toString();
            } catch (Throwable ignored) {
                a.appName = ai.packageName;
            }
            try {
                a.icon = ai.loadIcon(pm);
            } catch (Throwable ignored) {
                a.icon = null;
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

    private static class TemplateRow {
        String templateId = "";
        boolean isWhitelist = false;
        List<String> appList = new ArrayList<>();
    }

    private static class ScopeRow {
        String packageName = "";
        String displayName = "";
        android.graphics.drawable.Drawable icon;
        boolean isInstalled = true;
        boolean isSystemApp = false;
        boolean useWhitelist = false;
        boolean excludeSystemApps = true;
        List<String> applyTemplates = new ArrayList<>();
    }

    private static class InstalledApp {
        String appName = "";
        String packageName = "";
        android.graphics.drawable.Drawable icon;
    }

    private interface AppSelectionCallback {
        void onSelected(List<String> selectedPackages);
    }

    private interface TemplateNameSubmit {
        boolean onSubmit(String value);
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

