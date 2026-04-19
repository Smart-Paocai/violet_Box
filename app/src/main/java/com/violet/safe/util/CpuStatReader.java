package com.violet.safe.util;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 从 /proc/stat 解析聚合 cpu 行，用于计算占用率。
 */
public final class CpuStatReader {

    public final long user;
    public final long nice;
    public final long system;
    public final long idle;
    public final long iowait;
    public final long irq;
    public final long softirq;
    public final long steal;

    private CpuStatReader(long user, long nice, long system, long idle, long iowait,
                          long irq, long softirq, long steal) {
        this.user = user;
        this.nice = nice;
        this.system = system;
        this.idle = idle;
        this.iowait = iowait;
        this.irq = irq;
        this.softirq = softirq;
        this.steal = steal;
    }

    public static CpuStatReader read() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                return null;
            }
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]);
            long softirq = Long.parseLong(parts[7]);
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0L;
            return new CpuStatReader(user, nice, system, idle, iowait, irq, softirq, steal);
        } catch (Exception e) {
            return null;
        }
    }

    public long totalIdle() {
        return idle + iowait;
    }

    public long totalBusy() {
        return user + nice + system + irq + softirq + steal;
    }

    public long total() {
        return totalIdle() + totalBusy();
    }

    /**
     * @return { 总占用 0–100, 用户态占忙碌周期 0–100, IO 等待占全周期 0–100 }，异常时 null
     */
    public static int[] computePercents(CpuStatReader prev, CpuStatReader cur) {
        if (prev == null || cur == null) {
            return null;
        }
        long dTotal = cur.total() - prev.total();
        long dIdle = cur.totalIdle() - prev.totalIdle();
        long dBusy = cur.totalBusy() - prev.totalBusy();
        long dUser = cur.user - prev.user;
        long dNice = cur.nice - prev.nice;
        long dIo = cur.iowait - prev.iowait;
        if (dTotal <= 0) {
            return null;
        }
        int totalPct = (int) Math.min(100, Math.max(0, (100 * (dTotal - dIdle)) / dTotal));
        int userOfBusy = 0;
        if (dBusy > 0) {
            userOfBusy = (int) Math.min(100, Math.max(0, (100 * (dUser + dNice)) / dBusy));
        }
        int ioPct = (int) Math.min(100, Math.max(0, (100 * dIo) / dTotal));
        return new int[]{totalPct, userOfBusy, ioPct};
    }
}
