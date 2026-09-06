package com.violet.box.ui.safety;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.violet.box.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import rikka.shizuku.Shizuku;

/** Safety tab: selected intent, recovery journal and command outcome are kept separate. */
public class SafetyPageController implements SafetyAppAdapter.OnToggleListener {
    private static final int REQUEST_SHIZUKU_PERMISSION = 7001;
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_URL = "https://shizuku.rikka.app/zh-hans/";
    private static final String KEY_SHOW_SYSTEM = "show_system_apps";
    // Survives Activity recreation. Accepted operations finish journaling before a new
    // controller reads preferences; destroying a screen never interrupts a mutation.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "sensor-guard"));

    public static final class SafetyAppItem {
        public final String label, pkg, sharedSummary, stateText;
        public final Drawable icon;
        public final int uid, stateColor;
        public final boolean protectionRequested, canToggle;

        SafetyAppItem(AppEntry app, boolean requested, boolean canToggle, String stateText, int stateColor,
                      String sharedSummary) {
            label = app.label;
            pkg = app.info.packageName;
            uid = app.info.uid;
            icon = app.icon;
            protectionRequested = requested;
            this.canToggle = canToggle;
            this.stateText = stateText;
            this.stateColor = stateColor;
            this.sharedSummary = sharedSummary;
        }
    }

    private static final class AppEntry {
        final ApplicationInfo info;
        final String label;
        final Drawable icon;
        final List<String> group;
        AppEntry(ApplicationInfo info, String label, Drawable icon, List<String> group) {
            this.info = info;
            this.label = label;
            this.icon = icon;
            this.group = group;
        }
    }

    private enum ShizukuUi { CHECKING, NOT_INSTALLED, DEAD, UNAUTHORIZED, READY }

    private interface Operation { void run() throws Exception; }

    private final Activity activity;
    private final Context context;
    private final int userId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SafetyAppAdapter adapter = new SafetyAppAdapter(this);
    private final AtomicLong connectionEpoch = new AtomicLong();
    private final Shizuku.OnBinderReceivedListener receivedListener = this::onBinderChanged;
    private final Shizuku.OnBinderDeadListener deadListener = this::onBinderChanged;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code, result) -> {
        if (code == REQUEST_SHIZUKU_PERMISSION) onBinderChanged();
    };

    private TextView tvStatus, tvDetail, tvCmdState;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private Chip chipRestoreAll;
    private TextView tvProtectCount;
    private ImageView ivIcon;
    private MaterialCardView cardStatus;
    private RecyclerView rv;
    private com.google.android.material.chip.Chip chipShowSystem;
    private volatile boolean showSystem;
    private volatile boolean destroyed;
    // Main-thread only.
    private boolean busy, refreshPending;
    // Worker-thread only. Adapter receives fresh immutable snapshots.
    private List<AppEntry> apps = new ArrayList<>();
    private GuardSelection selection = new GuardSelection(Collections.emptySet(), Collections.emptySet());
    private final Map<Integer, String> outcomes = new HashMap<>();
    private final Set<Integer> applied = new HashSet<>();
    private ShizukuUi workerUi = ShizukuUi.CHECKING;
    private SafetyShell.Support support = SafetyShell.Support.UNKNOWN;
    private long reconciledEpoch = -1;
    private boolean retryNeeded = true;
    private String message = "";

    public SafetyPageController(Activity activity) {
        this.activity = activity;
        context = activity.getApplicationContext();
        userId = SensorCommand.userId(android.os.Process.myUid());
    }

    public void initialize() {
        SafetyShell.init(context);
        tvStatus = activity.findViewById(R.id.tvSafetyStatus);
        tvDetail = activity.findViewById(R.id.tvSafetyDetail);
        tvCmdState = activity.findViewById(R.id.tvSafetyCmdState);
        cardStatus = activity.findViewById(R.id.cardSafetyStatus);
        swipeRefresh = activity.findViewById(R.id.swipeSafetyRefresh);
        swipeRefresh.setOnRefreshListener(this::triggerRefresh);
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> rv != null && rv.canScrollVertically(-1));
        // 点击状态卡：未安装 Shizuku 时前往下载，其余情况等效于下拉刷新
        cardStatus.setOnClickListener(v -> {
            if (detectShizuku() == ShizukuUi.NOT_INSTALLED) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL)));
                } catch (Exception e) {
                    Toast.makeText(activity, "无法打开浏览器", Toast.LENGTH_SHORT).show();
                }
            } else {
                triggerRefresh();
            }
        });
        chipRestoreAll = activity.findViewById(R.id.chipRestoreAll);
        tvProtectCount = activity.findViewById(R.id.tvProtectCount);
        ivIcon = activity.findViewById(R.id.ivSafetyShizukuIcon);
        rv = activity.findViewById(R.id.rvSafetyApps);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setAdapter(adapter);
        chipShowSystem = activity.findViewById(R.id.chipShowSystem);
        showSystem = prefs().getBoolean(KEY_SHOW_SYSTEM, false);
        chipShowSystem.setChecked(showSystem);
        chipShowSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showSystem = isChecked;
            prefs().edit().putBoolean(KEY_SHOW_SYSTEM, isChecked).apply();
            submit(this::loadApps);
        });
        EditText search = activity.findViewById(R.id.etSafetySearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { adapter.setQuery(s.toString()); }
        });
        chipRestoreAll.setOnClickListener(v -> restoreAll());
        Shizuku.addBinderReceivedListenerSticky(receivedListener);
        Shizuku.addBinderDeadListener(deadListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        onMaybeShown();
    }

    public void destroy() {
        destroyed = true;
        Shizuku.removeBinderReceivedListener(receivedListener);
        Shizuku.removeBinderDeadListener(deadListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        main.removeCallbacksAndMessages(null);
        rv.setAdapter(null);
        IO.execute(SafetyShell::release);
    }

    public void onMaybeShown() {
        main.post(() -> {
            if (destroyed) return;
            if (busy) {
                refreshPending = true;
                return;
            }
            submit(this::refresh);
        });
    }

    /**
     * 延迟触发刷新：切页动画（380ms 滑动）期间创建行组合会与其抢主线程造成掉帧，
     * 因此切到安全页时由页面在动画结束后调用。
     */
    public void onMaybeShownDelayed(long delayMs) {
        if (destroyed) return;
        main.removeCallbacks(pendingShow);
        main.postDelayed(pendingShow, delayMs);
    }

    private final Runnable pendingShow = this::onMaybeShown;

    private void onBinderChanged() {
        connectionEpoch.incrementAndGet();
        onMaybeShown();
    }

    private void submit(Operation operation) {
        if (destroyed || busy) return;
        busy = true;
        chipRestoreAll.setEnabled(false);
        tvCmdState.setText("正在处理，请稍候…");
        IO.execute(() -> {
            message = "";
            try {
                operation.run();
            } catch (Exception e) {
                retryNeeded = true;
                applied.clear();
                outcomes.clear();
                message = "操作未完成：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                selection = readSelection();
            } finally {
                publish();
            }
        });
    }

    private void refresh() {
        if (destroyed) return;
        selection = readSelection();
        loadApps();
        long epoch = connectionEpoch.get();
        workerUi = detectShizuku();
        if (epoch != reconciledEpoch || workerUi != ShizukuUi.READY) {
            applied.clear();
            outcomes.clear();
        }
        if (workerUi != ShizukuUi.READY) {
            support = SafetyShell.Support.NO_SHIZUKU;
            retryNeeded = true;
            return;
        }
        // Never permanently cache a transient connection/permission failure.
        SafetyShell.SupportCheck check = SafetyShell.checkCommandSupport(context.getPackageName(), userId);
        support = check.support;
        if (support != SafetyShell.Support.SUPPORTED) {
            message = check.detail;
            applied.clear();
            retryNeeded = true;
            return;
        }
        if (epoch != reconciledEpoch || retryNeeded) {
            retryNeeded = false;
            reconcile();
            reconciledEpoch = epoch;
        }
        if (connectionEpoch.get() != epoch) {
            applied.clear();
            retryNeeded = true;
        }
    }

    private ShizukuUi detectShizuku() {
        try {
            // A working Binder also supports compatible Shizuku distributions.
            if (Shizuku.pingBinder()) {
                return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                        ? ShizukuUi.READY : ShizukuUi.UNAUTHORIZED;
            }
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return ShizukuUi.DEAD;
        } catch (PackageManager.NameNotFoundException e) {
            return ShizukuUi.NOT_INSTALLED;
        } catch (Exception e) {
            return ShizukuUi.DEAD;
        }
    }

    /** 下拉刷新/点击状态卡的重新检测；未授权时下拉即发起授权请求。 */
    private void triggerRefresh() {
        ShizukuUi current = detectShizuku();
        if (current == ShizukuUi.UNAUTHORIZED) {
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        connectionEpoch.incrementAndGet();
        onMaybeShown();
    }

    private void loadApps() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> previous = new HashMap<>();
        for (AppEntry entry : apps) previous.put(entry.info.packageName, entry);
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        Map<Integer, List<String>> groups = new HashMap<>();
        for (ApplicationInfo info : installed) {
            if (SensorCommand.userId(info.uid) == userId) {
                groups.computeIfAbsent(info.uid, key -> new ArrayList<>()).add(info.packageName);
            }
        }
        List<AppEntry> loaded = new ArrayList<>();
        for (ApplicationInfo info : installed) {
            if (SensorCommand.userId(info.uid) != userId) continue;
            // 系统应用没有开屏摇一摇广告，默认只列出用户应用；页内可打开“显示系统应用”
            if (!showSystem && (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            AppEntry cached = previous.get(info.packageName);
            String label = info.packageName;
            Drawable icon = pm.getDefaultActivityIcon();
            try { label = String.valueOf(info.loadLabel(pm)); } catch (Exception ignored) { }
            try {
                icon = cached != null && cached.info.uid == info.uid ? cached.icon : info.loadIcon(pm);
            } catch (Exception ignored) { }
            loaded.add(new AppEntry(info, label, icon, groups.get(info.uid)));
        }
        loaded.sort((a, b) -> {
            boolean au = (a.info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            boolean bu = (b.info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            return au != bu ? (au ? -1 : 1) : a.label.compareToIgnoreCase(b.label);
        });
        apps = loaded;
        // Icon failures/missing rows must never discard the recovery journal.
    }

    private List<String> resolveGroup(String pkg, int expectedUid) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
        if (SensorCommand.userId(info.uid) != userId || (expectedUid >= 0 && info.uid != expectedUid)) {
            throw new IllegalStateException("应用身份已变化，请重新检测");
        }
        String[] packages = pm.getPackagesForUid(info.uid);
        return packages == null ? Collections.singletonList(pkg) : Arrays.asList(packages);
    }

    @Override
    public void onToggle(SafetyAppItem item, boolean enable) {
        if (busy || destroyed) {
            adapter.restore(item); // 只让刚点击的行回弹，避免整页开关重绑闪烁
            return;
        }
        if (enable && !SafetyShell.shizukuReady()) {
            adapter.restore(item);
            onMaybeShown();
            return;
        }
        submit(() -> {
            selection = readSelection();
            List<String> group = resolveGroup(item.pkg, item.uid);
            selection.request(group, enable);
            saveSelection(); // Journal first, including requests whose Binder result may be lost.
            executeGroup(item.pkg, item.uid, group, enable);
        });
    }

    /** False stops the batch after connection loss/timeout; all remaining intent stays journaled. */
    private boolean executeGroup(String pkg, int uid, List<String> group, boolean enable) {
        applied.remove(uid);
        if (destroyed) return false; // Next controller/session will reconcile the saved intent.
        if (enable && uid % 100000 < 10000) {
            outcomes.put(uid, "系统保留身份，不支持限制；可恢复已有记录");
            return true;
        }
        SafetyShell.Result result = enable ? SafetyShell.setIdle(pkg, userId) : SafetyShell.reset(pkg, userId);
        if (result.ok) {
            if (enable) {
                applied.add(uid);
                outcomes.put(uid, "已限制");
            } else {
                selection.restored(group);
                saveSelection();
                outcomes.remove(uid);
            }
        } else {
            retryNeeded = true;
            outcomes.put(uid, (enable ? "待重试：" : "待恢复：") + result.describe());
            message = result.describe();
        }
        return !result.isTransportFailure();
    }

    private void restoreAll() {
        submit(() -> {
            selection = readSelection();
            selection.request(selection.managed(), false);
            saveSelection();
            applied.clear();
            outcomes.clear();
            reconcile();
            message = selection.managed().isEmpty() ? "已恢复全部应用传感器"
                    : "仍有 " + selection.managed().size() + " 个应用待恢复，记录已保留";
        });
    }

    private void reconcile() {
        Set<Integer> visited = new HashSet<>();
        for (String pkg : selection.managed()) {
            if (destroyed) break;
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
                List<String> group = resolveGroup(pkg, info.uid);
                if (!visited.add(info.uid)) continue;
                boolean enable = !selection.wantsRestore(group);
                // Migrate legacy per-package selections to the full shared-UID group.
                selection.request(group, enable);
                saveSelection();
                if (!executeGroup(pkg, info.uid, group, enable)) break;
            } catch (PackageManager.NameNotFoundException e) {
                retryNeeded = true;
                message = "部分应用已卸载或不可访问，恢复记录已保留";
            }
        }
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("sensor_guard", Context.MODE_PRIVATE);
    }

    private GuardSelection readSelection() {
        SharedPreferences prefs = prefs();
        String protectKey = "protected_packages_u" + userId;
        Set<String> saved = prefs.getStringSet(protectKey, Collections.emptySet());
        // Old builds always targeted user 0. Never import those settings into another user.
        if (userId == 0 && !prefs.contains(protectKey)) {
            saved = prefs.getStringSet("protected_packages", Collections.emptySet());
        }
        return new GuardSelection(saved,
                prefs.getStringSet("restore_pending_packages_u" + userId, Collections.emptySet()));
    }

    private void saveSelection() {
        SharedPreferences.Editor edit = prefs().edit()
                .putStringSet("protected_packages_u" + userId, new HashSet<>(selection.protect))
                .putStringSet("restore_pending_packages_u" + userId, new HashSet<>(selection.restore));
        if (userId == 0) edit.remove("protected_packages");
        if (!edit.commit()) throw new IllegalStateException("无法保存恢复记录，请重试");
    }

    private void publish() {
        if (destroyed) return;
        final ShizukuUi ui = detectShizuku();
        final SafetyShell.Support commandSupport = support;
        final boolean currentSession = connectionEpoch.get() == reconciledEpoch && ui == ShizukuUi.READY
                && commandSupport == SafetyShell.Support.SUPPORTED;
        List<SafetyAppItem> snapshot = new ArrayList<>();
        Map<String, String> labels = new HashMap<>();
        for (AppEntry app : apps) labels.put(app.info.packageName, app.label);
        int appliedCount = 0;
        for (AppEntry app : apps) {
            boolean managed = selection.manages(app.group);
            boolean confirmed = currentSession && applied.contains(app.info.uid) && !selection.wantsRestore(app.group);
            String state = "";
            if (managed) {
                state = selection.wantsRestore(app.group) ? "待恢复" : "待下发";
                if (currentSession) state = outcomes.getOrDefault(app.info.uid, state);
                if (ui != ShizukuUi.READY) state = "状态待确认";
            }
            if (confirmed) appliedCount++;
            List<String> peers = new ArrayList<>();
            for (String pkg : app.group) if (!pkg.equals(app.info.packageName)) peers.add(labels.getOrDefault(pkg, pkg));
            String shared = peers.isEmpty() ? "" : "同步控制：" + android.text.TextUtils.join("、", peers);
            boolean controllable = app.info.uid % 100000 >= 10000;
            if (!controllable) state = "系统保留身份，不支持限制";
            int color = ContextCompat.getColor(context, confirmed ? R.color.ios_semantic_positive : R.color.ios_text_secondary);
            snapshot.add(new SafetyAppItem(app, selection.wantsProtection(app.group),
                    controllable && (managed || (ui == ShizukuUi.READY && commandSupport == SafetyShell.Support.SUPPORTED)),
                    state, color, shared));
        }
        final int appliedApps = appliedCount;
        final int managedApps = selection.managed().size();
        final String notice = message;
        main.post(() -> {
            if (destroyed) return;
            busy = false;
            adapter.submit(snapshot);
            // 芯片常驻可点：submit 期间被禁用，每次操作结束统一恢复
            chipRestoreAll.setEnabled(true);
            swipeRefresh.setRefreshing(false);
            renderStatus(ui, commandSupport);
            String summary;
            switch (commandSupport) {
                case SUPPORTED: {
                    int pending = Math.max(0, managedApps - appliedApps);
                    summary = managedApps == 0 ? "尚未开启任何应用保护"
                            : pending == 0 ? "已保护 " + appliedApps + " 个应用"
                            : "已保护 " + appliedApps + " 个 · 待下发 " + pending + " 个";
                    break;
                }
                case NOT_SUPPORTED: summary = "当前系统未提供传感器限制命令"; break;
                case NO_PERMISSION: summary = "系统拒绝了传感器管理权限，可下拉重新检测"; break;
                default: summary = "状态待确认，可下拉重新检测"; break;
            }
            boolean readyOk = ui == ShizukuUi.READY && commandSupport == SafetyShell.Support.SUPPORTED;
            if (readyOk) {
                tvProtectCount.setText(String.valueOf(appliedApps));
                int pending = Math.max(0, managedApps - appliedApps);
                List<String> extras = new ArrayList<>();
                if (pending > 0) extras.add("待下发 " + pending + " 个");
                if (!notice.isEmpty()) extras.add(notice);
                if (extras.isEmpty()) {
                    tvCmdState.setVisibility(View.GONE);
                } else {
                    tvCmdState.setVisibility(View.VISIBLE);
                    tvCmdState.setText(String.join("\n", extras));
                }
            } else {
                tvProtectCount.setText("–");
                tvCmdState.setVisibility(View.VISIBLE);
                tvCmdState.setText(summary + (notice.isEmpty() ? "" : "\n" + notice));
            }
            if (refreshPending) {
                refreshPending = false;
                onMaybeShown();
            }
        });
    }

    private void renderStatus(ShizukuUi ui, SafetyShell.Support commandSupport) {
        switch (ui) {
            case NOT_INSTALLED:
                tvStatus.setText("Shizuku：未安装");
                tvDetail.setText("未检测到 Shizuku；点击本卡片前往下载，启动服务后下拉重新检测");
                break;
            case DEAD:
                tvStatus.setText("Shizuku：服务未运行");
                tvDetail.setText("请启动 Shizuku 服务，完成后下拉本页重新检测；恢复记录会保留");
                break;
            case UNAUTHORIZED:
                tvStatus.setText("Shizuku：等待授权");
                tvDetail.setText("授权后可管理应用传感器，未完成的恢复会继续处理");
                break;
            case READY:
                tvStatus.setText("Shizuku已激活");
                tvDetail.setText("通过限制指定应用的加速度传感器，阻止摇一摇广告的触发与跳转");
                break;
            default:
                tvStatus.setText("Shizuku：检测中...");
                break;
        }
        // KernelSU 首页 StatusCard 模式：容器色调直接表达状态
        boolean ready = ui == ShizukuUi.READY && commandSupport == SafetyShell.Support.SUPPORTED;
        boolean problem = (ui == ShizukuUi.NOT_INSTALLED || ui == ShizukuUi.DEAD || ui == ShizukuUi.UNAUTHORIZED)
                && commandSupport != SafetyShell.Support.SUPPORTED;
        int titleRes = ready ? R.color.explore_emerald_600
                : problem ? R.color.explore_amber_600 : R.color.ios_text_primary;
        int detailRes = ready || problem ? R.color.explore_slate_700 : R.color.ios_text_secondary;
        int bgRes = ready ? R.color.explore_emerald_100
                : problem ? R.color.explore_amber_100 : R.color.ios_glass_surface;
        cardStatus.setCardBackgroundColor(ContextCompat.getColor(context, bgRes));
        tvStatus.setTextColor(ContextCompat.getColor(context, titleRes));
        tvDetail.setTextColor(ContextCompat.getColor(context, detailRes));
        tvCmdState.setTextColor(ContextCompat.getColor(context, detailRes));
        ivIcon.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, titleRes)));
    }
}
