package com.violet.safe.util;

import android.os.Build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/**
 * /proc/cpuinfo、cpufreq 等 sysfs 读取。
 */
public final class CpuSysFiles {

    private CpuSysFiles() {
    }

    /** 示例： aarch64 · 8 核 · Snapdragon ... */
    public static String buildCpuSummaryLine() {
        int cores = Runtime.getRuntime().availableProcessors();
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        String hardware = readCpuInfoField("Hardware");
        if (hardware == null) {
            hardware = Build.HARDWARE;
        }
        return abi + " · " + cores + " 核 · " + hardware;
    }

    private static String readCpuInfoField(String keyPrefix) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.regionMatches(true, 0, keyPrefix, 0, keyPrefix.length())) {
                    int idx = t.indexOf(':');
                    if (idx >= 0 && idx + 1 < t.length()) {
                        return t.substring(idx + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** scaling_cur_freq，单位 kHz → GHz 字符串 */
    public static String readCpu0CurFreqGHz() {
        Long khz = readLongFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
        if (khz == null || khz <= 0) {
            return "—";
        }
        return String.format(Locale.getDefault(), "%.2f GHz", khz / 1_000_000d);
    }

    private static Long readLongFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line != null) {
                return Long.parseLong(line.trim());
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
