package com.violet.safe.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * SELinux 状态读取：与玩机「SELinux 管理」一致优先 {@code su getenforce} 读内核真实模式，
 * 失败时再按 反射 API → 系统 getenforce → sysfs enforce → status 节点 回退。
 */
public final class SelinuxStatusReader {

    private static final String TAG = "SELinux";
    private static final long SU_GETENFORCE_TIMEOUT_MS = 2500L;

    private SelinuxStatusReader() {
    }

    private static String firstMeaningfulLine(String stdout) {
        if (stdout == null) {
            return "";
        }
        for (String part : stdout.split("\\r?\\n")) {
            if (part != null) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    /**
     * 读取 /sys/fs/selinux/status 中的 “SELinux status:” 行。
     *
     * @return null 无法解析；true 已启用；false 完全未启用
     */
    public static Boolean readEnabledFromStatusNode() {
        File statusFile = new File("/sys/fs/selinux/status");
        if (!statusFile.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (!t.startsWith("SELinux status:")) {
                    continue;
                }
                String rest = t.substring(t.indexOf(':') + 1).trim().toLowerCase();
                if (rest.contains("disabled")) {
                    return Boolean.FALSE;
                }
                if (rest.contains("enabled")) {
                    return Boolean.TRUE;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** /sys/fs/selinux/enforce：1=强制，0=宽容 */
    public static String readEnforceNodeOrNull() {
        File enforceFile = new File("/sys/fs/selinux/enforce");
        if (!enforceFile.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(enforceFile))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String readEnforceViaGetenforce() {
        String[] getenforcePaths = {
                "/system/bin/getenforce",
                "/vendor/bin/getenforce",
                "/system_ext/bin/getenforce"
        };
        for (String path : getenforcePaths) {
            if (!new File(path).isFile()) {
                continue;
            }
            try {
                Process p = new ProcessBuilder(path).redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = r.readLine();
                    if (p.waitFor() == 0 && line != null) {
                        String l = line.trim().toLowerCase();
                        if (l.equals("enforcing") || l.equals("permissive") || l.equals("disabled")) {
                            return l;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 反射 {@link android.os.SELinux}，返回 enforcing / permissive / disabled，失败为 null */
    public static String readApiModeOrNull() {
        try {
            Class<?> selinuxClass = Class.forName("android.os.SELinux");
            Method isSELinuxEnabled = selinuxClass.getMethod("isSELinuxEnabled");
            Method isSELinuxEnforced = selinuxClass.getMethod("isSELinuxEnforced");

            boolean apiEnabled = (boolean) isSELinuxEnabled.invoke(null);
            Log.d(TAG, "API - Enabled: " + apiEnabled);
            if (!apiEnabled) {
                return "disabled";
            }
            boolean enforced = (boolean) isSELinuxEnforced.invoke(null);
            Log.d(TAG, "API - Enforced: " + enforced);
            return enforced ? "enforcing" : "permissive";
        } catch (Exception e) {
            Log.e(TAG, "API检测失败", e);
        }
        return null;
    }

    /**
     * 中文结论（含英文括号），与「SELinux 管理」页内核判定优先一致：{@code su -c getenforce}。
     */
    public static String getStatusString() {
        SelinuxShellUtil.ShellResult su = SelinuxShellUtil.runSu("getenforce", SU_GETENFORCE_TIMEOUT_MS);
        if (su.success) {
            String line = firstMeaningfulLine(su.stdout);
            String norm = SelinuxShellUtil.normalizeGetenforceOutput(line);
            Log.d(TAG, "su getenforce raw=" + line + " norm=" + norm);
            if (SelinuxShellUtil.MODE_ENFORCING.equals(norm)) {
                return "严格模式 (Enforcing)";
            }
            if (SelinuxShellUtil.MODE_PERMISSIVE.equals(norm)) {
                return "宽容模式 (Permissive)";
            }
            if ("disabled".equals(norm)) {
                return "关闭状态 (SELinux 未启用)";
            }
        }

        try {
            Class<?> selinuxClass = Class.forName("android.os.SELinux");
            Method isSELinuxEnabled = selinuxClass.getMethod("isSELinuxEnabled");
            Method isSELinuxEnforced = selinuxClass.getMethod("isSELinuxEnforced");

            Boolean enabled = (Boolean) isSELinuxEnabled.invoke(null);
            Log.d(TAG, "反射API - Enabled: " + enabled);
            if (enabled != null && !enabled) {
                return "关闭状态 (SELinux 未启用)";
            }
            Boolean enforced = (Boolean) isSELinuxEnforced.invoke(null);
            Log.d(TAG, "反射API - Enforced: " + enforced);
            if (enforced != null) {
                return enforced ? "严格模式 (Enforcing)" : "宽容模式 (Permissive)";
            }
        } catch (Exception e) {
            Log.e(TAG, "反射API调用失败", e);
        }

        String getenforceVal = readEnforceViaGetenforce();
        Log.d(TAG, "getenforce命令返回值: " + getenforceVal);
        if (getenforceVal != null) {
            if ("enforcing".equals(getenforceVal)) {
                return "严格模式 (Enforcing)";
            }
            if ("permissive".equals(getenforceVal)) {
                return "宽容模式 (Permissive)";
            }
            if ("disabled".equals(getenforceVal)) {
                return "关闭状态 (SELinux 未启用)";
            }
        }

        String enforceVal = readEnforceNodeOrNull();
        Log.d(TAG, "enforce文件返回值: " + enforceVal);
        if (enforceVal != null) {
            if ("1".equals(enforceVal)) {
                return "严格模式 (Enforcing)";
            }
            if ("0".equals(enforceVal)) {
                return "宽容模式 (Permissive)";
            }
        }

        Boolean enabledNode = readEnabledFromStatusNode();
        Log.d(TAG, "status文件返回值: " + enabledNode);
        if (Boolean.FALSE.equals(enabledNode)) {
            return "关闭状态 (SELinux 未启用)";
        }

        return "未知 (权限不足或系统不支持)";
    }
}
