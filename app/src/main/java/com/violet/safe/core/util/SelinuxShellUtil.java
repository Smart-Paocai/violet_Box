package com.violet.safe.core.util;

import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 与 {@link com.violet.safe.ui.selinux.SelinuxManagerActivity} 一致：通过 {@code su -c} 执行命令，
 * 用于读取内核真实 {@code getenforce} 结果（部分环境下 Java API 与 sysfs 不可靠）。
 */
public final class SelinuxShellUtil {

    public static final String MODE_ENFORCING = "enforcing";
    public static final String MODE_PERMISSIVE = "permissive";

    private SelinuxShellUtil() {
    }

    public static final class ShellResult {
        public final boolean success;
        public final String stdout;
        public final String stderr;

        public ShellResult(boolean success, String stdout, String stderr) {
            this.success = success;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
        }
    }

    /**
     * 与 {@code ProcessBuilder("su", "-c", command)} 行为一致；带超时避免主线程误用时卡死。
     */
    public static ShellResult runSu(String command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean finished = process.waitFor(Math.max(200L, timeoutMs), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroy();
                    return new ShellResult(false, "", "timeout");
                }
            } else {
                process.waitFor();
            }
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exit = process.exitValue();
            return new ShellResult(exit == 0, stdout, stderr);
        } catch (Exception e) {
            return new ShellResult(false, "", e.getMessage() == null ? "" : e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** 与 {@link com.violet.safe.ui.selinux.SelinuxManagerActivity} 内解析逻辑一致：解析 getenforce 单行输出 */
    public static String normalizeGetenforceOutput(String modeRaw) {
        if (modeRaw == null) {
            return "";
        }
        String lower = modeRaw.trim().toLowerCase();
        if (lower.startsWith(MODE_ENFORCING)) {
            return MODE_ENFORCING;
        }
        if (lower.startsWith(MODE_PERMISSIVE)) {
            return MODE_PERMISSIVE;
        }
        if (lower.startsWith("disabled")) {
            return "disabled";
        }
        return lower;
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
}
