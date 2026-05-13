package com.violet.safe.ui.module;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModuleBackupActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ModuleAdapter adapter;
    private CheckBox cbSelectAll;
    private TextView tvStatus;
    private ExtendedFloatingActionButton fabBackup;
    private final List<ModuleItem> moduleList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static class ModuleItem {
        String id;
        String name;
        String version;
        String author;
        String description;
        String dir;
        boolean isSelected = false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module_backup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvStatus = findViewById(R.id.tvStatus);
        fabBackup = findViewById(R.id.fabBackup);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ModuleAdapter();
        recyclerView.setAdapter(adapter);

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ModuleItem item : moduleList) {
                item.isSelected = isChecked;
            }
            adapter.notifyDataSetChanged();
            updateFabState();
        });

        fabBackup.setOnClickListener(v -> startBackupProcess());

        loadModules();
    }

    private void updateFabState() {
        int count = 0;
        for (ModuleItem item : moduleList) {
            if (item.isSelected) count++;
        }
        if (count > 0) {
            fabBackup.setText("备份所选 (" + count + ")");
            fabBackup.setVisibility(View.VISIBLE);
        } else {
            fabBackup.setVisibility(View.GONE);
        }
    }

    private void loadModules() {
        tvStatus.setText("正在扫描模块...");
        fabBackup.setVisibility(View.GONE);
        new Thread(() -> {
            List<ModuleItem> list = new ArrayList<>();
            try {
                Process process = new ProcessBuilder("su", "-c", 
                    "for dir in /data/adb/modules/*; do " +
                    "if [ -f \"$dir/module.prop\" ]; then " +
                    "  id=$(grep '^id=' \"$dir/module.prop\" | cut -d'=' -f2-); " +
                    "  name=$(grep '^name=' \"$dir/module.prop\" | cut -d'=' -f2-); " +
                    "  version=$(grep '^version=' \"$dir/module.prop\" | cut -d'=' -f2-); " +
                    "  author=$(grep '^author=' \"$dir/module.prop\" | cut -d'=' -f2-); " +
                    "  description=$(grep '^description=' \"$dir/module.prop\" | cut -d'=' -f2-); " +
                    "  echo \"$dir|||$id|||$name|||$version|||$author|||$description\"; " +
                    "fi; " +
                    "done"
                ).redirectErrorStream(true).start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|\\|\\|", -1);
                    if (parts.length >= 6) {
                        ModuleItem item = new ModuleItem();
                        item.dir = parts[0].trim();
                        item.id = parts[1].trim();
                        item.name = parts[2].trim();
                        item.version = parts[3].trim();
                        item.author = parts[4].trim();
                        item.description = parts[5].trim();
                        if (item.name.isEmpty()) item.name = item.id;
                        list.add(item);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }

            mainHandler.post(() -> {
                moduleList.clear();
                moduleList.addAll(list);
                adapter.notifyDataSetChanged();
                tvStatus.setText("共找到 " + list.size() + " 个模块");
                cbSelectAll.setChecked(false);
                updateFabState();
            });
        }).start();
    }

    private void startBackupProcess() {
        List<ModuleItem> selected = new ArrayList<>();
        for (ModuleItem item : moduleList) {
            if (item.isSelected) selected.add(item);
        }
        if (selected.isEmpty()) return;

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle("正在备份");
        dialog.setMessage("准备中...");
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            String backupDir = "/storage/emulated/0/Magisk模块备份";
            try {
                new ProcessBuilder("su", "-c", "mkdir -p \"" + backupDir + "\"").start().waitFor();
            } catch (Exception ignored) {}

            int success = 0;
            int failed = 0;
            StringBuilder errorLogs = new StringBuilder();

            for (int i = 0; i < selected.size(); i++) {
                ModuleItem item = selected.get(i);
                final int current = i + 1;
                mainHandler.post(() -> dialog.setMessage("正在备份 (" + current + "/" + selected.size() + ")\n" + item.name));

                String error = doBackupModule(item.dir, backupDir, item.name, item.version);
                if (error == null) {
                    success++;
                } else {
                    failed++;
                    errorLogs.append("【").append(item.name).append("】失败日志:\n").append(error).append("\n\n");
                }
            }

            final int fSuccess = success;
            final int fFailed = failed;
            final String fErrorLogs = errorLogs.toString();
            mainHandler.post(() -> {
                dialog.dismiss();
                AlertDialog.Builder builder = new AlertDialog.Builder(ModuleBackupActivity.this)
                        .setTitle("备份完成")
                        .setMessage("成功: " + fSuccess + "\n失败: " + fFailed + "\n\n备份目录: " + backupDir + (fFailed > 0 ? "\n\n错误日志:\n" + fErrorLogs : ""))
                        .setPositiveButton("确定", null);
                if (fFailed > 0) {
                    builder.setNeutralButton("复制日志", (dialogInterface, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Backup Error Logs", fErrorLogs);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(ModuleBackupActivity.this, "错误日志已复制", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                builder.show();
            });
        }).start();
    }

    private String doBackupModule(String moduleDir, String backupDir, String name, String version) {
        // 过滤空指针以避免 replaceAll 抛出 NullPointerException
        String safeName = name != null ? name.replaceAll("[\\\\/:*?\"<>| ]", "_") : "unknown";
        String safeVersion = version != null ? version.replaceAll("[\\\\/:*?\"<>| ]", "_") : "unknown";
        String backupPath = backupDir + "/" + safeName + "_v" + safeVersion + ".zip";
        
        String appCacheDir = getCacheDir().getAbsolutePath();
        String tempDirName = "module_backup_" + System.currentTimeMillis();
        String tempDir = appCacheDir + "/" + tempDirName;
        String tempZip = appCacheDir + "/backup_" + System.currentTimeMillis() + ".zip";

        try {
            // 将整个备份脚本写入到一个临时文件，避免 ProcessBuilder 参数过长和引号转义问题
            String wrapperScript = 
                "script_file=\"/data/local/tmp/violet_backup_script_$$.sh\"\n" +
                "cat > \"$script_file\" <<'INNER_EOF'\n" +
                "module_dir=\"" + moduleDir + "\"\n" +
                "temp_dir=\"" + tempDir + "\"\n" +
                "app_cache=\"" + appCacheDir + "\"\n" +
                "rm -rf \"$temp_dir\"\n" +
                "mkdir -p \"$temp_dir\"\n" +
                "cp -a \"$module_dir/.\" \"$temp_dir/\" 2>/dev/null\n" +
                "rm -f \"$temp_dir/.gitignore\"\n" +
                "perm_script=\"$temp_dir/customize.sh\"\n" +
                "cat > \"$perm_script\" <<'PERM_EOF'\n" +
                "#!/system/bin/sh\n" +
                "SKIPUNZIP=0\n" +
                "ui_print \"- Repacked module snapshot\"\n" +
                "ui_print \"- Restoring permissions\"\n" +
                "set_perm_recursive \"$MODPATH\" 0 0 0755 0644\n" +
                "PERM_EOF\n" +
                "cd \"$module_dir\" || exit 1\n" +
                "find . -type d | while IFS= read -r item; do\n" +
                "    file_path=\"${item#./}\"\n" +
                "    [ -z \"$file_path\" ] && continue\n" +
                "    [ \"$file_path\" = \".\" ] && continue\n" +
                "    stat -c \"%u %g %a\" \"$item\" 2>/dev/null | while read -r uid gid perm; do\n" +
                "        [ -z \"$uid\" ] && continue\n" +
                "        if [ ${#perm} -eq 3 ]; then perm=\"0$perm\"; fi\n" +
                "        echo \"set_perm \\\"\\$MODPATH/$file_path\\\" $uid $gid $perm\" >> \"$perm_script\"\n" +
                "    done\n" +
                "done\n" +
                "find . -type f | while IFS= read -r item; do\n" +
                "    file_path=\"${item#./}\"\n" +
                "    [ -z \"$file_path\" ] && continue\n" +
                "    if [ \"$file_path\" = \"customize.sh\" ]; then continue; fi\n" +
                "    stat -c \"%u %g %a\" \"$item\" 2>/dev/null | while read -r uid gid perm; do\n" +
                "        [ -z \"$uid\" ] && continue\n" +
                "        if [ ${#perm} -eq 3 ]; then perm=\"0$perm\"; fi\n" +
                "        echo \"set_perm \\\"\\$MODPATH/$file_path\\\" $uid $gid $perm\" >> \"$perm_script\"\n" +
                "    done\n" +
                "done\n" +
                "find . -type l | while IFS= read -r item; do\n" +
                "    file_path=\"${item#./}\"\n" +
                "    [ -z \"$file_path\" ] && continue\n" +
                "    target=$(readlink \"$item\")\n" +
                "    echo \"ln -s \\\"$target\\\" \\\"\\$MODPATH/$file_path\\\"\" >> \"$perm_script\"\n" +
                "done\n" +
                "find \"$temp_dir\" -type l -delete\n" +
                "cat >> \"$perm_script\" <<'PERM_EOF'\n" +
                "[ -f \"$MODPATH/service.sh\" ] && chmod 0755 \"$MODPATH/service.sh\"\n" +
                "[ -f \"$MODPATH/post-fs-data.sh\" ] && chmod 0755 \"$MODPATH/post-fs-data.sh\"\n" +
                "[ -f \"$MODPATH/action.sh\" ] && chmod 0755 \"$MODPATH/action.sh\"\n" +
                "[ -f \"$MODPATH/uninstall.sh\" ] && chmod 0755 \"$MODPATH/uninstall.sh\"\n" +
                "find \"$MODPATH\" -type f -name \"*.sh\" -exec chmod 0755 {} \\; 2>/dev/null\n" +
                "find \"$MODPATH\" -type f -name \"zygiskd\" -exec chmod 0755 {} \\; 2>/dev/null\n" +
                "find \"$MODPATH\" -type f -name \"magiskpolicy\" -exec chmod 0755 {} \\; 2>/dev/null\n" +
                "find \"$MODPATH\" -type f -name \"dex2oat\" -exec chmod 0755 {} \\; 2>/dev/null\n" +
                "find \"$MODPATH\" -type f -name \"*.so\" -exec chmod 0644 {} \\; 2>/dev/null\n" +
                "ui_print \"- Permission restore finished\"\n" +
                "PERM_EOF\n" +
                "chmod 0755 \"$perm_script\"\n" +
                "chown -R $(stat -c \"%u:%g\" \"$app_cache\") \"$temp_dir\"\n" +
                "chmod -R 777 \"$temp_dir\"\n" +
                "INNER_EOF\n" +
                "sh \"$script_file\"\n" +
                "res=$?\n" +
                "rm -f \"$script_file\"\n" +
                "exit $res";
            
            Process process = new ProcessBuilder("su", "-c", wrapperScript).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder logBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                logBuilder.append(line).append("\n");
            }
            process.waitFor();
            if (process.exitValue() != 0) {
                return "执行准备脚本失败:\n" + logBuilder.toString().trim();
            }

            // 使用 Java 自身的 Zip API 进行打包
            try {
                zipDirectory(new File(tempDir), new File(tempZip));
            } catch (Exception e) {
                return "Java 打包 Zip 失败: " + e.getMessage();
            }

            // 打包完成后，使用 su 将文件移动到目标目录，并清理临时文件
            String moveScript = 
                "mv \"" + tempZip + "\" \"" + backupPath + "\"\n" +
                "res=$?\n" +
                "rm -rf \"" + tempDir + "\"\n" +
                "exit $res";
            Process mvProc = new ProcessBuilder("su", "-c", moveScript).redirectErrorStream(true).start();
            BufferedReader mvReader = new BufferedReader(new InputStreamReader(mvProc.getInputStream()));
            StringBuilder mvLog = new StringBuilder();
            String mvLine;
            while ((mvLine = mvReader.readLine()) != null) {
                mvLog.append(mvLine).append("\n");
            }
            mvProc.waitFor();
            if (mvProc.exitValue() != 0) {
                return "移动备份文件失败:\n" + mvLog.toString().trim();
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private void zipDirectory(File dir, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFile(dir, dir, zos);
        }
    }

    private void zipFile(File rootDir, File sourceFile, ZipOutputStream zos) throws IOException {
        if (sourceFile.isDirectory()) {
            File[] files = sourceFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    zipFile(rootDir, file, zos);
                }
            }
        } else {
            String path = rootDir.toURI().relativize(sourceFile.toURI()).getPath();
            ZipEntry zipEntry = new ZipEntry(path);
            zos.putNextEntry(zipEntry);
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
            }
            zos.closeEntry();
        }
    }

    private class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_module_backup, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ModuleItem item = moduleList.get(position);
            holder.tvName.setText(item.name);
            holder.tvDesc.setText(item.description);
            holder.tvVersion.setText("v" + item.version + (item.author != null && !item.author.isEmpty() ? " | " + item.author : ""));
            holder.cbModule.setChecked(item.isSelected);

            holder.itemView.setOnClickListener(v -> {
                item.isSelected = !item.isSelected;
                holder.cbModule.setChecked(item.isSelected);
                updateFabState();
                
                boolean allSelected = true;
                for (ModuleItem m : moduleList) {
                    if (!m.isSelected) {
                        allSelected = false;
                        break;
                    }
                }
                cbSelectAll.setChecked(allSelected);
            });
        }

        @Override
        public int getItemCount() {
            return moduleList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDesc, tvVersion;
            CheckBox cbModule;

            public VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvModuleName);
                tvDesc = itemView.findViewById(R.id.tvModuleDesc);
                tvVersion = itemView.findViewById(R.id.tvModuleVersion);
                cbModule = itemView.findViewById(R.id.cbModule);
            }
        }
    }
}