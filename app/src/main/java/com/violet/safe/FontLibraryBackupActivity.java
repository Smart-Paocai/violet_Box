package com.violet.safe;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class FontLibraryBackupActivity extends AppCompatActivity {

    private static final String PREF_NAME = "font_library_backup";
    private static final String KEY_EXCLUDE_SUPER = "exclude_super";
    private static final String KEY_COMPRESS_PACK = "compress_pack";

    /** 与 {@link PartitionManagerActivity} 中展示的分区枚举方式一致，下列分区名不参与字库备份 */
    private static final Set<String> FONT_LIBRARY_EXCLUDED_NAMES = new HashSet<>(Arrays.asList(
            "sda", "sdb", "sdc", "sdd", "sde", "sdf", "userdata"
    ));

    private TextView tvFontBackupStatus;
    private MaterialButton btnFontBackupExport;
    private SwitchMaterial switchExcludeSuper;
    private SwitchMaterial switchCompressPack;
    private ActivityResultLauncher<Intent> saveArchiveLauncher;
    private ActivityResultLauncher<Uri> openTreeLauncher;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_library_backup);

        Toolbar toolbar = findViewById(R.id.toolbarFontBackup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("字库备份");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvFontBackupStatus = findViewById(R.id.tvFontBackupStatus);
        btnFontBackupExport = findViewById(R.id.btnFontBackupExport);
        switchExcludeSuper = findViewById(R.id.switchExcludeSuper);
        switchCompressPack = findViewById(R.id.switchCompressPack);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        switchExcludeSuper.setChecked(prefs.getBoolean(KEY_EXCLUDE_SUPER, true));
        switchExcludeSuper.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_EXCLUDE_SUPER, isChecked).apply());

        switchCompressPack.setChecked(prefs.getBoolean(KEY_COMPRESS_PACK, true));
        switchCompressPack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_COMPRESS_PACK, isChecked).apply();
            updateExportButtonLabel();
        });

        saveArchiveLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        runBackupWorker(true, uri, null);
                    }
                });

        openTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    try {
                        final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(uri, flags);
                    } catch (SecurityException ignored) {
                    }
                    runBackupWorker(false, null, uri);
                });

        btnFontBackupExport.setOnClickListener(v -> startPickSaveLocation());
        updateExportButtonLabel();
    }

    private void updateExportButtonLabel() {
        if (switchCompressPack.isChecked()) {
            btnFontBackupExport.setText("选择保存位置并备份");
        } else {
            btnFontBackupExport.setText("选择文件夹并备份");
        }
    }

    private void startPickSaveLocation() {
        if (busy) {
            return;
        }
        if (switchCompressPack.isChecked()) {
            String name = "VioletBox_Backup_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".tar.gz";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/gzip");
            intent.putExtra(Intent.EXTRA_TITLE, name);
            saveArchiveLauncher.launch(intent);
        } else {
            openTreeLauncher.launch(null);
        }
    }

    /**
     * @param compressPack true：打 tar.gz 并写入 {@code archiveUri}；false：将各 .img 写入目录树 {@code treeUri}
     */
    private void runBackupWorker(boolean compressPack, @Nullable Uri archiveUri, @Nullable Uri treeUri) {
        final boolean excludeSuper = switchExcludeSuper.isChecked();
        setBusy(true, "状态：正在读取分区表…");
        new Thread(() -> {
            List<PartitionItem> all = fetchPartitionTableLikePartitionManager();
            List<PartitionItem> targets = filterFontLibraryPartitions(all, excludeSuper);
            if (targets.isEmpty()) {
                runOnUiThread(() -> {
                    setBusy(false, "状态：未授予ROOT）");
                    Toast.makeText(this, "没有需要备份的分区", Toast.LENGTH_LONG).show();
                });
                return;
            }

            File staging = new File(getCacheDir(), "font_lib_staging_" + System.currentTimeMillis());
            File bundle = new File(getCacheDir(), "font_library_bundle.tgz");
            if (bundle.exists()) {
                //noinspection ResultOfMethodCallIgnored
                bundle.delete();
            }

            runOnUiThread(() -> tvFontBackupStatus.setText(
                    "状态：将备份 " + targets.size() + " 个字库分区…"));

            //noinspection ResultOfMethodCallIgnored
            staging.mkdirs();

            try {
                int n = targets.size();
                for (int i = 0; i < n; i++) {
                    PartitionItem p = targets.get(i);
                    int step = i + 1;
                    runOnUiThread(() -> tvFontBackupStatus.setText(
                            "状态：正在导出 " + p.name + " (" + step + "/" + n + ")…"));

                    File outImg = new File(staging, safeImageFileName(p.name));
                    ShellResult dd = runSuCommand(
                            "dd if=" + shellEscape(p.path) + " of=" + shellEscape(outImg.getAbsolutePath()) + " bs=4M");
                    if (!dd.success) {
                        runOnUiThread(() -> {
                            setBusy(false, "状态：导出失败（" + p.name + "）");
                            Toast.makeText(this, "dd 失败: " + p.name, Toast.LENGTH_LONG).show();
                        });
                        wipeStaging(staging);
                        //noinspection ResultOfMethodCallIgnored
                        bundle.delete();
                        return;
                    }
                }

                if (compressPack) {
                    if (archiveUri == null) {
                        wipeStaging(staging);
                        return;
                    }
                    runOnUiThread(() -> tvFontBackupStatus.setText("状态：正在压缩打包…"));
                    ShellResult tar = runSuCommand(
                            "tar -czf " + shellEscape(bundle.getAbsolutePath()) + " -C "
                                    + shellEscape(staging.getAbsolutePath()) + " ."
                                    + " && chmod 644 " + shellEscape(bundle.getAbsolutePath()));
                    wipeStaging(staging);

                    if (!tar.success || !bundle.isFile() || bundle.length() == 0L) {
                        runOnUiThread(() -> {
                            setBusy(false, "状态：打包失败");
                            Toast.makeText(this, "打包失败", Toast.LENGTH_SHORT).show();
                        });
                        //noinspection ResultOfMethodCallIgnored
                        bundle.delete();
                        return;
                    }

                    boolean ok = copyFileToUri(bundle, archiveUri);
                    //noinspection ResultOfMethodCallIgnored
                    bundle.delete();
                    runOnUiThread(() -> {
                        setBusy(false, ok ? "状态：已写入所选位置" : "状态：写入失败");
                        Toast.makeText(this, ok ? "字库备份完成" : "导出失败", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    if (treeUri == null) {
                        wipeStaging(staging);
                        runOnUiThread(() -> setBusy(false, "状态：未选择目录"));
                        return;
                    }
                    runOnUiThread(() -> tvFontBackupStatus.setText("状态：正在写入所选文件夹…"));
                    boolean ok = copyStagingImgsToTree(staging, treeUri);
                    wipeStaging(staging);
                    //noinspection ResultOfMethodCallIgnored
                    bundle.delete();
                    runOnUiThread(() -> {
                        setBusy(false, ok ? "状态：已写入所选文件夹" : "状态：写入失败");
                        Toast.makeText(this, ok ? "字库导出完成" : "写入文件夹失败", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                wipeStaging(staging);
                //noinspection ResultOfMethodCallIgnored
                bundle.delete();
                runOnUiThread(() -> {
                    setBusy(false, "状态：失败");
                    Toast.makeText(this, "备份异常", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /** 在目录树中新建时间戳子文件夹，并将 staging 内各 .img 写入。 */
    private boolean copyStagingImgsToTree(File stagingDir, Uri treeUri) {
        try {
            ContentResolver resolver = getContentResolver();
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);

            String folderName = "violet_font_library_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            Uri dirUri;
            try {
                dirUri = DocumentsContract.createDocument(
                        resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, folderName);
            } catch (Exception e) {
                dirUri = null;
            }
            if (dirUri == null) {
                dirUri = parentUri;
            }

            File[] files = stagingDir.listFiles();
            if (files == null || files.length == 0) {
                return false;
            }
            for (File f : files) {
                if (!f.isFile()) {
                    continue;
                }
                Uri out = DocumentsContract.createDocument(
                        resolver, dirUri, "application/octet-stream", f.getName());
                if (out == null) {
                    return false;
                }
                if (!copyFileToUri(f, out)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 与 {@link PartitionManagerActivity#refreshPartitionList} 一致：ls /dev/block/by-name + readlink。
     */
    private List<PartitionItem> fetchPartitionTableLikePartitionManager() {
        List<PartitionItem> result = new ArrayList<>();
        ShellResult listResult = runSuCommand("ls -1 /dev/block/by-name 2>/dev/null");
        if (!listResult.success) {
            return result;
        }
        String[] lines = listResult.stdout.split("\n");
        for (String line : lines) {
            String name = line.trim();
            if (name.isEmpty()) {
                continue;
            }
            ShellResult pathResult = runSuCommand("readlink -f /dev/block/by-name/" + shellEscape(name));
            String path = pathResult.success ? pathResult.stdout.trim() : "";
            if (path.isEmpty()) {
                path = "/dev/block/by-name/" + name;
            }
            result.add(new PartitionItem(name, path));
        }
        Collections.sort(result, Comparator.comparing(a -> a.name));
        return result;
    }

    private static List<PartitionItem> filterFontLibraryPartitions(
            List<PartitionItem> all, boolean excludeSuper) {
        List<PartitionItem> out = new ArrayList<>();
        for (PartitionItem p : all) {
            if (FONT_LIBRARY_EXCLUDED_NAMES.contains(p.name)) {
                continue;
            }
            if (excludeSuper && "super".equals(p.name)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    /** by-name 分区名通常安全；若含路径分隔符则替换，避免构造错误路径 */
    private static String safeImageFileName(String partitionName) {
        String s = partitionName.replace('/', '_');
        return s + ".img";
    }

    private void wipeStaging(File staging) {
        runSuCommand("rm -rf " + shellEscape(staging.getAbsolutePath()));
    }

    private void setBusy(boolean value, @Nullable String statusText) {
        busy = value;
        btnFontBackupExport.setEnabled(!value);
        switchExcludeSuper.setEnabled(!value);
        switchCompressPack.setEnabled(!value);
        if (statusText != null) {
            tvFontBackupStatus.setText(statusText);
        }
    }

    private boolean copyFileToUri(File src, Uri dst) {
        try (FileInputStream fis = new FileInputStream(src);
             OutputStream os = getContentResolver().openOutputStream(dst)) {
            if (os == null) {
                return false;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
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

    private static class PartitionItem {
        final String name;
        final String path;

        PartitionItem(String name, String path) {
            this.name = name;
            this.path = path;
        }
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
