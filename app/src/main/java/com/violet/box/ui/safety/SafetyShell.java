package com.violet.box.ui.safety;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rikka.shizuku.Shizuku;

/** Bounded Shizuku connection and RPC lifetime; reset returns sensor policy to Android. */
public final class SafetyShell {
    public enum Support { UNKNOWN, SUPPORTED, NO_PERMISSION, NOT_SUPPORTED, NO_SHIZUKU }

    public static final class SupportCheck {
        public final Support support;
        public final String detail;

        SupportCheck(Support support, String detail) {
            this.support = support;
            this.detail = detail;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final int exitCode;
        public final String output;
        public final String error;

        Result(boolean ok, int exitCode, String output, String error) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }

        public String describe() {
            if (ok) return "成功";
            if (!error.isEmpty()) return error;
            return output.trim().isEmpty() ? "失败 (exit=" + exitCode + ")" : output.trim();
        }

        boolean isTransportFailure() {
            return !ok && (exitCode < 0 || exitCode == 124 || exitCode == 125);
        }
    }

    private static final Object LOCK = new Object();
    // A stuck synchronous Binder call cannot be interrupted. Refuse new calls instead
    // of accumulating blocked threads or queued mutations after a reported timeout.
    private static final ThreadPoolExecutor RPC = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), r -> {
                Thread thread = new Thread(r, "sensor-guard-rpc");
                thread.setDaemon(true);
                return thread;
            });
    private static Context appContext;
    private static Shizuku.UserServiceArgs serviceArgs;
    private static Binding binding;
    private static int owners;

    private static final class Binding implements ServiceConnection {
        final CountDownLatch ready = new CountDownLatch(1);
        volatile IShellService service;

        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) {
                if (binding == this && binder.pingBinder()) service = IShellService.Stub.asInterface(binder);
                ready.countDown();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) {
                service = null;
                if (binding == this) binding = null;
                ready.countDown();
            }
        }

        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    }

    private SafetyShell() { }

    public static void init(Context context) {
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            owners++;
            if (serviceArgs == null) {
                serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(appContext, ShellService.class))
                        .processNameSuffix("safety_shell").daemon(false).version(2);
            }
        }
    }

    /** Called on the shared worker after accepted operations have finished. */
    public static void release() {
        synchronized (LOCK) {
            if (owners > 0) owners--;
            if (owners != 0) return;
            Binding old = binding;
            binding = null;
            if (old != null) {
                old.ready.countDown();
                try { Shizuku.unbindUserService(serviceArgs, old, true); } catch (Exception ignored) { }
            }
        }
    }

    public static boolean shizukuReady() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private static IShellService ensureService(long deadline) throws Exception {
        Binding attempt;
        synchronized (LOCK) {
            if (binding != null && binding.service != null && binding.service.asBinder().pingBinder()) {
                return binding.service;
            }
            if (binding != null && binding.ready.getCount() == 0) binding = null;
            if (binding == null) {
                binding = new Binding();
                try {
                    Shizuku.bindUserService(serviceArgs, binding);
                } catch (Exception e) {
                    binding.ready.countDown();
                    binding = null;
                    throw e;
                }
            }
            attempt = binding;
        }
        long waitMs = Math.min(4000L, deadline - SystemClock.elapsedRealtime());
        boolean connected;
        try {
            connected = waitMs > 0 && attempt.ready.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            discardBinding(attempt);
            throw e;
        }
        if (!connected) {
            discardBinding(attempt);
            throw new TimeoutException("连接 Shizuku 服务超时，可重新检测");
        }
        if (attempt.service == null || !attempt.service.asBinder().pingBinder()) {
            throw new IllegalStateException("Shizuku 服务已断开，请重新检测");
        }
        return attempt.service;
    }

    private static void discardBinding(Binding attempt) {
        synchronized (LOCK) {
            if (binding == attempt) binding = null;
        }
        try { Shizuku.unbindUserService(serviceArgs, attempt, false); } catch (Exception ignored) { }
    }

    public static Result exec(String[] command, long timeoutMs) {
        if (!SensorCommand.valid(command) || timeoutMs <= 0) return failure("无效命令或超时时间");
        if (!shizukuReady()) return failure("Shizuku 未就绪，状态待确认");
        synchronized (LOCK) {
            if (appContext == null) return failure("内部未初始化");
        }
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        Future<Result> call;
        try {
            call = RPC.submit(() -> {
                IShellService service = ensureService(deadline);
                if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline) {
                    return failure("执行超时，结果待确认");
                }
                String[] result = service.runCommand(command, deadline);
                if (result == null || result.length < 2) return failure("服务返回无效结果");
                int code = Integer.parseInt(result[0]);
                return new Result(code == 0, code, result[1], "");
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return failure("上一条命令尚未结束，请稍后重试或重启 Shizuku");
        }
        try {
            return call.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            RPC.remove((Runnable) call);
            return failure("执行超时，结果待确认");
        } catch (InterruptedException e) {
            call.cancel(true);
            RPC.remove((Runnable) call);
            Thread.currentThread().interrupt();
            return failure("执行被中断，结果待确认");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return failure("执行失败：" + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : " · " + cause.getMessage()));
        }
    }

    static Result failure(String message) { return new Result(false, -1, "", message); }

    public static SupportCheck checkCommandSupport(String selfPackage, int userId) {
        if (!shizukuReady()) return new SupportCheck(Support.NO_SHIZUKU, "Shizuku 已断开，请重新启动并检测");
        Result result = exec(SensorCommand.build("get-uid-state", selfPackage, userId), 8000);
        return classifyCommandSupport(result);
    }

    static SupportCheck classifyCommandSupport(Result result) {
        String state = result.output.trim();
        // AOSP handleGetUidState returns dprintf's byte count, not NO_ERROR:
        // "active\n" -> 7, "idle\n" -> 5. Some ROMs instead return 0.
        // This exception applies only to this read-only query; set/reset still require 0.
        boolean validState = "active".equals(state) || "idle".equals(state);
        if (validState && result.error.isEmpty()
                && (result.exitCode == 0 || result.exitCode == state.length() + 1)) {
            return new SupportCheck(Support.SUPPORTED, "");
        }
        String error = (result.output + "\n" + result.error).toLowerCase(Locale.ROOT);
        Support support = Support.UNKNOWN;
        if (error.contains("permission") || error.contains("securityexception")) support = Support.NO_PERMISSION;
        if (error.contains("unknown command") || error.contains("can't find service")
                || error.contains("not found")) support = Support.NOT_SUPPORTED;
        String reason = result.error.isEmpty() ? result.output.trim() : result.error;
        if (reason.isEmpty()) reason = "未收到有效的传感器状态";
        if (reason.length() > 240) reason = reason.substring(0, 240) + "…";
        return new SupportCheck(support, "检测未通过（exit=" + result.exitCode + "）：" + reason);
    }

    public static Result setIdle(String pkg, int userId) {
        return exec(SensorCommand.build("set-uid-state", pkg, userId), 8000);
    }

    public static Result reset(String pkg, int userId) {
        return exec(SensorCommand.build("reset-uid-state", pkg, userId), 8000);
    }
}
