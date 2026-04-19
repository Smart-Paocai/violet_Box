package com.violet.safe;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.scottyab.rootbeer.RootBeer;
import com.violet.safe.model.InstalledAppRow;
import com.violet.safe.util.SelinuxShellUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用管理：列出用户 / 系统应用，提取 APK 到「下载」目录、分享、Root 卸载/冻结与数据备份等。
 */
public class AppManagerActivity extends AppCompatActivity {

    private static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider";
    /** 导出 APK 子目录（在系统「下载」下） */
    private static final String DOWNLOAD_APK_SUBDIR = "VioletApk";
    /** Root 备份 tar 输出目录（用户可见「下载」下） */
    private static final String DOWNLOAD_BACKUP_SUBDIR = "VioletAppBackup";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService loadExecutor;

    private TabLayout tabScope;
    private EditText etSearch;
    private RecyclerView rvApps;
    private ProgressBar progress;

    private final List<InstalledAppRow> userApps = new ArrayList<>();
    private final List<InstalledAppRow> systemApps = new ArrayList<>();
    private int tabIndex;
    private String filterText = "";

    private InstalledAppAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manager);

        Toolbar toolbar = findViewById(R.id.toolbarAppManager);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("应用管理");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabScope = findViewById(R.id.tabAppScope);
        etSearch = findViewById(R.id.etAppSearch);
        rvApps = findViewById(R.id.rvInstalledApps);
        progress = findViewById(R.id.progressAppList);

        tabScope.addTab(tabScope.newTab().setText("用户应用"));
        tabScope.addTab(tabScope.newTab().setText("系统应用"));
        tabScope.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                tabIndex = tab.getPosition();
                refreshFilteredList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        adapter = new InstalledAppAdapter(getPackageManager(), this::showActionsDialog);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterText = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                refreshFilteredList();
            }
        });

        loadExecutor = Executors.newSingleThreadExecutor();
        reloadAppsAsync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadExecutor != null) {
            loadExecutor.shutdownNow();
            loadExecutor = null;
        }
    }

    private void reloadAppsAsync() {
        progress.setVisibility(View.VISIBLE);
        rvApps.setVisibility(View.INVISIBLE);
        ExecutorService ex = loadExecutor;
        if (ex == null) {
            return;
        }
        PackageManager pm = getPackageManager();
        ex.execute(() -> {
            List<InstalledAppRow> u = new ArrayList<>();
            List<InstalledAppRow> s = new ArrayList<>();
            try {
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                Collator coll = Collator.getInstance(Locale.CHINA);
                for (ApplicationInfo ai : apps) {
                    if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                        continue;
                    }
                    try {
                        CharSequence labelCs = pm.getApplicationLabel(ai);
                        String label = labelCs != null ? labelCs.toString() : ai.packageName;
                        boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        InstalledAppRow row = new InstalledAppRow(ai, label, system);
                        if (system) {
                            s.add(row);
                        } else {
                            u.add(row);
                        }
                    } catch (Exception ignored) {
                    }
                }
                u.sort((a, b) -> coll.compare(a.label, b.label));
                s.sort((a, b) -> coll.compare(a.label, b.label));
            } catch (Exception ignored) {
            }
            mainHandler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                userApps.clear();
                userApps.addAll(u);
                systemApps.clear();
                systemApps.addAll(s);
                progress.setVisibility(View.GONE);
                rvApps.setVisibility(View.VISIBLE);
                refreshFilteredList();
            });
        });
    }

    private void refreshFilteredList() {
        List<InstalledAppRow> source = tabIndex == 0 ? userApps : systemApps;
        if (filterText.isEmpty()) {
            adapter.submitList(source);
            return;
        }
        List<InstalledAppRow> out = new ArrayList<>();
        for (InstalledAppRow r : source) {
            if (r.label.toLowerCase(Locale.ROOT).contains(filterText)
                    || r.appInfo.packageName.toLowerCase(Locale.ROOT).contains(filterText)) {
                out.add(r);
            }
        }
        adapter.submitList(out);
    }

    private void showActionsDialog(@NonNull InstalledAppRow row) {
        final String pkg = row.appInfo.packageName;
        boolean isSelf = pkg.equals(getPackageName());

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add("复制包名");
        actions.add(() -> copyPackageName(pkg));

        labels.add("提取APK安装包");
        actions.add(() -> extractApk(row));

        labels.add("分享APK安装包");
        actions.add(() -> shareApk(row));

        if (!isSelf) {
            labels.add("卸载");
            actions.add(() -> uninstallPackage(pkg));
        }

        labels.add("冻结（ROOT）");
        actions.add(() -> freezePackage(pkg, true));

        labels.add("解冻（ROOT）");
        actions.add(() -> freezePackage(pkg, false));

        labels.add("备份应用数据（ROOT）");
        actions.add(() -> backupAppData(row));

        String[] arr = labels.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(row.label)
                .setItems(arr, (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void copyPackageName(String pkg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("package", pkg));
            Toast.makeText(this, "已复制包名", Toast.LENGTH_SHORT).show();
        }
    }

    /** 分享/保存时 APK 文件名：仅应用名称，不含包名。 */
    private static String apkDisplayFileName(InstalledAppRow row) {
        return safeFileToken(row.label) + ".apk";
    }

    private void extractApk(InstalledAppRow row) {
        String src = row.appInfo.sourceDir;
        if (src == null || src.isEmpty()) {
            Toast.makeText(this, "无法读取安装路径", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在导出到「下载」…", Toast.LENGTH_SHORT).show();
        ExecutorService ex = loadExecutor;
        if (ex == null) {
            return;
        }
        final String displayName = apkDisplayFileName(row);
        ex.execute(() -> {
            String resultMsg;
            boolean ok = false;
            try {
                saveApkToPublicDownloads(new File(src), displayName);
                resultMsg = "已保存到「/sdcard/Download/" + DOWNLOAD_APK_SUBDIR + "/" + displayName + "」";
                ok = true;
            } catch (IOException e) {
                String remote = "/sdcard/Download/" + DOWNLOAD_APK_SUBDIR + "/" + safeAsciiApkFileName(displayName);
                SelinuxShellUtil.ShellResult mk = SelinuxShellUtil.runSu(
                        "mkdir -p " + shellQuote("/sdcard/Download/" + DOWNLOAD_APK_SUBDIR), 10_000L);
                SelinuxShellUtil.ShellResult cp = SelinuxShellUtil.runSu(
                        "cp " + shellQuote(src) + " " + shellQuote(remote), 60_000L);
                if (mk.success && cp.success) {
                    resultMsg = "已保存（Root）到「下载/" + DOWNLOAD_APK_SUBDIR + "」\n" + remote
                            + "\n（若文件名含特殊字符，Root 路径已自动改为 ASCII 文件名）";
                    ok = true;
                } else {
                    resultMsg = "导出失败：" + trimOneLine(cp.stderr + cp.stdout);
                }
            }
            final boolean fOk = ok;
            final String fMsg = resultMsg;
            final InstalledAppRow fRow = row;
            mainHandler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (fOk) {
                    String splitNote = "";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && fRow.appInfo.splitSourceDirs != null
                            && fRow.appInfo.splitSourceDirs.length > 0) {
                        splitNote = "\n（含分包时此处仅导出主 APK）";
                    }
                    Toast.makeText(this, fMsg + splitNote, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, fMsg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 写入系统「下载」目录下子文件夹；Android 10+ 使用 MediaStore，更早版本写公共 Download。
     */
    private void saveApkToPublicDownloads(File srcApk, String displayName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_APK_SUBDIR);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new IOException("MediaStore 插入失败");
            }
            try (InputStream in = new FileInputStream(srcApk);
                 OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) {
                    throw new IOException("无法打开输出流");
                }
                copyStream(in, os);
            }
            return;
        }
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(dl, DOWNLOAD_APK_SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        File dest = new File(dir, displayName);
        copyApkFile(srcApk, dest);
    }

    private void shareApk(InstalledAppRow row) {
        String src = row.appInfo.sourceDir;
        if (src == null || src.isEmpty()) {
            Toast.makeText(this, "无法读取安装路径", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = apkDisplayFileName(row);
        File cache = new File(getCacheDir(), "share-" + name);
        try {
            copyApkFile(new File(src), cache);
        } catch (IOException e) {
            SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu(
                    "cp " + shellQuote(src) + " " + shellQuote(cache.getAbsolutePath()), 60_000L);
            if (!r.success || !cache.exists() || cache.length() == 0) {
                Toast.makeText(this, "无法读取 APK（可尝试 Root）", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Uri uri = FileProvider.getUriForFile(this, getFileProviderAuthority(), cache);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/vnd.android.package-archive");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, name);
        send.putExtra(Intent.EXTRA_TITLE, name);
        send.setClipData(ClipData.newRawUri(name, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "分享APK安装包"));
    }

    private void uninstallPackage(String pkg) {
        if (pkg.equals(getPackageName())) {
            Toast.makeText(this, "不能卸载本应用", Toast.LENGTH_SHORT).show();
            return;
        }
        RootBeer rb = new RootBeer(this);
        if (rb.isRooted() && isSafePackageToken(pkg)) {
            if (trySilentUninstall(pkg)) {
                Toast.makeText(this, "已通过 Root 静默卸载", Toast.LENGTH_SHORT).show();
                reloadAppsAsync();
                return;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统卸载", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean trySilentUninstall(String pkg) {
        SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu("pm uninstall --user 0 " + pkg, 60_000L);
        if (isPmUninstallSuccess(r)) {
            return true;
        }
        r = SelinuxShellUtil.runSu("pm uninstall " + pkg, 60_000L);
        return isPmUninstallSuccess(r);
    }

    private static boolean isPmUninstallSuccess(SelinuxShellUtil.ShellResult r) {
        if (!r.success) {
            return false;
        }
        String out = (r.stdout + "\n" + r.stderr).toLowerCase(Locale.US);
        return out.contains("success") || out.contains("成功");
    }

    private void freezePackage(String pkg, boolean freeze) {
        if (freeze && pkg.equals(getPackageName())) {
            Toast.makeText(this, "不能冻结本应用", Toast.LENGTH_SHORT).show();
            return;
        }
        RootBeer rb = new RootBeer(this);
        if (!rb.isRooted()) {
            Toast.makeText(this, "冻结/解冻需要 Root", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isSafePackageToken(pkg)) {
            Toast.makeText(this, "包名异常，已拒绝执行", Toast.LENGTH_SHORT).show();
            return;
        }
        String cmd = freeze
                ? "pm disable-user --user 0 " + pkg
                : "pm enable " + pkg;
        SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu(cmd, 20_000L);
        if (r.success) {
            Toast.makeText(this, freeze ? "已尝试冻结（部分机型需重启生效）" : "已尝试解冻", Toast.LENGTH_SHORT).show();
            reloadAppsAsync();
        } else {
            Toast.makeText(this, "执行失败: " + trimOneLine(r.stderr), Toast.LENGTH_LONG).show();
        }
    }

    private void backupAppData(InstalledAppRow row) {
        RootBeer rb = new RootBeer(this);
        if (!rb.isRooted()) {
            Toast.makeText(this, "备份应用数据需要 Root", Toast.LENGTH_SHORT).show();
            return;
        }
        String pkg = row.appInfo.packageName;
        if (!isSafePackageToken(pkg)) {
            Toast.makeText(this, "包名异常，已拒绝执行", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在备份…", Toast.LENGTH_SHORT).show();
        ExecutorService ex = loadExecutor;
        if (ex == null) {
            return;
        }
        ex.execute(() -> {
            String baseName = safeFileToken(row.label) + "_" + System.currentTimeMillis() + ".tar";
            baseName = baseName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            String remoteDir = "/sdcard/Download/" + DOWNLOAD_BACKUP_SUBDIR;
            String outPath = remoteDir + "/" + baseName;

            SelinuxShellUtil.runSu("mkdir -p " + shellQuote(remoteDir), 15_000L);

            String[] dataRoots = {"/data/user/0", "/data/data"};
            boolean done = false;
            String lastErr = "";
            for (String dataRoot : dataRoots) {
                String dirPath = dataRoot + "/" + pkg;
                SelinuxShellUtil.ShellResult exists = SelinuxShellUtil.runSu("test -d " + shellQuote(dirPath), 10_000L);
                if (!exists.success) {
                    continue;
                }
                SelinuxShellUtil.runSu("rm -f " + shellQuote(outPath), 5_000L);
                // 无压缩 tar（避免 -z 在部分 ROM 上找不到 gzip）；先试系统 tar，再试 toybox
                String cmd = "tar cf " + shellQuote(outPath) + " -C " + shellQuote(dataRoot) + " " + shellQuote(pkg);
                SelinuxShellUtil.ShellResult r = SelinuxShellUtil.runSu(cmd, 180_000L);
                if (!r.success || !SelinuxShellUtil.runSu("test -s " + shellQuote(outPath), 5_000L).success) {
                    cmd = "toybox tar cf " + shellQuote(outPath) + " -C " + shellQuote(dataRoot) + " " + shellQuote(pkg);
                    r = SelinuxShellUtil.runSu(cmd, 180_000L);
                }
                SelinuxShellUtil.ShellResult hasFile = SelinuxShellUtil.runSu("test -s " + shellQuote(outPath), 5_000L);
                if (r.success && hasFile.success) {
                    done = true;
                    break;
                }
                lastErr = trimOneLine(r.stderr + r.stdout);
                SelinuxShellUtil.runSu("rm -f " + shellQuote(outPath), 5_000L);
            }

            final boolean fDone = done;
            final String fPath = outPath;
            final String fErr = lastErr;
            mainHandler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (fDone) {
                    Toast.makeText(this,
                            "备份完成\n「/sdcard/Download/" + DOWNLOAD_BACKUP_SUBDIR + "」\n" + fPath,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            "备份失败（未找到数据目录或 tar 失败）\n" + (fErr.isEmpty() ? "请确认该应用已产生 /data 数据" : fErr),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static void copyApkFile(File src, File dest) throws IOException {
        if (dest.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
        }
        try (FileChannel in = new FileInputStream(src).getChannel();
             FileChannel out = new FileOutputStream(dest).getChannel()) {
            in.transferTo(0, in.size(), out);
        }
    }

    private static String shellQuote(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    /** Root 写 /sdcard 时避免非 ASCII 路径导致失败，保留 .apk 后缀。 */
    private static String safeAsciiApkFileName(String displayName) {
        if (displayName == null || !displayName.endsWith(".apk")) {
            return "app.apk";
        }
        String base = displayName.substring(0, displayName.length() - 4);
        String ascii = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (ascii.isEmpty()) {
            ascii = "app";
        }
        if (ascii.length() > 80) {
            ascii = ascii.substring(0, 80);
        }
        return ascii + ".apk";
    }

    private static String safeFileToken(String label) {
        if (label == null || label.isEmpty()) {
            return "app";
        }
        String t = label.replaceAll("[^\\p{L}\\p{N}_\\-]+", "_");
        if (t.length() > 80) {
            t = t.substring(0, 80);
        }
        return t.isEmpty() ? "app" : t;
    }

    /** 防止包名中含 shell 元字符导致注入。 */
    private static boolean isSafePackageToken(String pkg) {
        if (pkg == null || pkg.length() < 2 || pkg.length() > 250 || !pkg.contains(".")) {
            return false;
        }
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String trimOneLine(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replace('\n', ' ');
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    private String getFileProviderAuthority() {
        return getPackageName() + FILE_PROVIDER_AUTHORITY_SUFFIX;
    }
}
