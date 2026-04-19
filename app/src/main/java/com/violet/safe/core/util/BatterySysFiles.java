package com.violet.safe.core.util;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 电池 sysfs 后备读取（部分机型 {@link android.os.BatteryManager#getIntProperty(int)} 恒为 0 或未实现）。
 */
public final class BatterySysFiles {

    private BatterySysFiles() {
    }

    private static final String[] VOLTAGE_NOW_PATHS = {
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/Battery/voltage_now",
            "/sys/class/power_supply/bms/voltage_now",
    };

    private static final String[] CURRENT_NOW_PATHS = {
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/Battery/current_now",
            "/sys/class/power_supply/bms/current_now",
    };

    private static final String[] CHARGE_FULL_DESIGN_PATHS = {
            "/sys/class/power_supply/battery/charge_full_design_uah",
            "/sys/class/power_supply/Battery/charge_full_design_uah",
            "/sys/class/power_supply/bms/charge_full_design_uah",
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/Battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
    };

    private static final String[] CHARGE_FULL_PATHS = {
            "/sys/class/power_supply/battery/charge_full_uah",
            "/sys/class/power_supply/Battery/charge_full_uah",
            "/sys/class/power_supply/bms/charge_full_uah",
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/Battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
    };

    private static long readLongFirstLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line == null) {
                return Long.MIN_VALUE;
            }
            line = line.trim();
            if (line.isEmpty()) {
                return Long.MIN_VALUE;
            }
            return Long.parseLong(line);
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * 瞬时电流，内核多为微安（µA）；无法读取时为 {@link Long#MIN_VALUE}。
     */
    public static long readCurrentNowMicroAmps() {
        for (String p : CURRENT_NOW_PATHS) {
            long v = readLongFirstLine(p);
            if (v != Long.MIN_VALUE) {
                return v;
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * 端电压，多为微伏（µV）；无法读取时为 {@link Long#MIN_VALUE}。
     */
    public static long readVoltageNowMicroVolts() {
        for (String p : VOLTAGE_NOW_PATHS) {
            long v = readLongFirstLine(p);
            if (v != Long.MIN_VALUE && v != 0L) {
                return v;
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * 设计满电容量，多为微安时（µAh）；无法读取时为 {@link Long#MIN_VALUE}。
     */
    public static long readChargeFullDesignMicroAh() {
        for (String p : CHARGE_FULL_DESIGN_PATHS) {
            long v = readLongFirstLine(p);
            if (v != Long.MIN_VALUE && v > 0L) {
                return v;
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * 当前标称满充容量（可能随老化低于设计值），µAh。
     */
    public static long readChargeFullMicroAh() {
        for (String p : CHARGE_FULL_PATHS) {
            long v = readLongFirstLine(p);
            if (v != Long.MIN_VALUE && v > 0L) {
                return v;
            }
        }
        return Long.MIN_VALUE;
    }

    /**
     * 部分设备在 Android 14 以下也通过 sysfs 暴露循环次数。
     *
     * @return &gt;= 0 为有效；否则 -1
     */
    public static int readCycleCount() {
        String[] paths = {
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/Battery/cycle_count",
        };
        for (String p : paths) {
            long v = readLongFirstLine(p);
            if (v >= 0L && v <= Integer.MAX_VALUE) {
                return (int) v;
            }
        }
        return -1;
    }
}
