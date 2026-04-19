package com.violet.safe.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 /proc/stat 解析聚合 cpu 行与各 cpuN 行，用于计算占用率。
 */
public final class CpuStatReader {

    private static final Pattern CPU_CORE_FIRST = Pattern.compile("(?i)^cpu(\\d+)$");
    /** 聚合行：cpu 后须为空白再跟数字，避免与 cpu0、cpu10 混淆 */
    private static final Pattern LINE_AGGREGATE_CPU = Pattern.compile("(?i)^cpu\\s+\\d");

    public final long user;
    public final long nice;
    public final long system;
    public final long idle;
    public final long iowait;
    public final long irq;
    public final long softirq;
    public final long steal;
    public final long guest;
    public final long guestNice;

    private CpuStatReader(long user, long nice, long system, long idle, long iowait,
                          long irq, long softirq, long steal, long guest, long guestNice) {
        this.user = user;
        this.nice = nice;
        this.system = system;
        this.idle = idle;
        this.iowait = iowait;
        this.irq = irq;
        this.softirq = softirq;
        this.steal = steal;
        this.guest = guest;
        this.guestNice = guestNice;
    }

    public static CpuStatReader read() {
        ProcCpuSnapshot full = readProcCpuSnapshot();
        return full != null ? full.aggregate : null;
    }

    public static final class ProcCpuSnapshot {
        public final CpuStatReader aggregate;
        public final CpuStatReader[] perCpu;

        private ProcCpuSnapshot(CpuStatReader aggregate, CpuStatReader[] perCpu) {
            this.aggregate = aggregate;
            this.perCpu = perCpu != null ? perCpu : new CpuStatReader[0];
        }
    }

    public static ProcCpuSnapshot readProcCpuSnapshot() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            ArrayList<String> lines = new ArrayList<>();
            String line;
            int cap = 0;
            while ((line = br.readLine()) != null && cap < 768) {
                cap++;
                String t = trimBom(line.trim());
                if (!t.isEmpty()) {
                    lines.add(t);
                }
            }

            int maxIdx = -1;
            for (String t : lines) {
                String[] tok = t.split("\\s+");
                if (tok.length < 1) {
                    continue;
                }
                Matcher m = CPU_CORE_FIRST.matcher(tok[0]);
                if (m.matches()) {
                    maxIdx = Math.max(maxIdx, Integer.parseInt(m.group(1)));
                }
            }

            CpuStatReader[] perCpu = maxIdx >= 0 ? new CpuStatReader[maxIdx + 1] : new CpuStatReader[0];
            CpuStatReader aggregate = null;

            for (String t : lines) {
                String[] tok = t.split("\\s+");
                if (tok.length < 8) {
                    continue;
                }
                String first = tok[0];
                if (aggregate == null && LINE_AGGREGATE_CPU.matcher(t).find()) {
                    aggregate = parseStatLine(t);
                    continue;
                }
                Matcher m = CPU_CORE_FIRST.matcher(first);
                if (m.matches()) {
                    int idx = Integer.parseInt(m.group(1));
                    CpuStatReader one = parseStatLine(t);
                    if (one != null && idx >= 0 && idx < perCpu.length) {
                        perCpu[idx] = one;
                    }
                }
            }

            if (aggregate == null) {
                return null;
            }
            return new ProcCpuSnapshot(aggregate, perCpu);
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static CpuStatReader parseStatLine(String line) {
        try {
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
            long guest = parts.length > 9 ? Long.parseLong(parts[9]) : 0L;
            long guestNice = parts.length > 10 ? Long.parseLong(parts[10]) : 0L;
            return new CpuStatReader(user, nice, system, idle, iowait, irq, softirq, steal, guest, guestNice);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 /proc/stat 解析出的数组补长到与 UI 核数一致（右侧补 null），避免 sysfs 核数多于 stat 行数时错位。
     */
    public static CpuStatReader[] alignPerCpuLength(CpuStatReader[] src, int coreCount) {
        if (coreCount <= 0) {
            return src != null ? src : new CpuStatReader[0];
        }
        if (src == null) {
            return new CpuStatReader[coreCount];
        }
        if (src.length >= coreCount) {
            return src;
        }
        CpuStatReader[] out = new CpuStatReader[coreCount];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    public static int[] computePerCorePercents(CpuStatReader[] prev, CpuStatReader[] cur) {
        if (prev == null || cur == null) {
            return null;
        }
        int n = Math.min(prev.length, cur.length);
        if (n == 0) {
            return new int[0];
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            CpuStatReader a = prev[i];
            CpuStatReader b = cur[i];
            if (a == null || b == null) {
                out[i] = 0;
                continue;
            }
            long dTotal = b.total() - a.total();
            long dIdle = b.totalIdle() - a.totalIdle();
            if (dTotal <= 0) {
                out[i] = 0;
            } else {
                out[i] = (int) Math.min(100, Math.max(0, (100 * (dTotal - dIdle)) / dTotal));
            }
        }
        return out;
    }

    public long totalIdle() {
        return idle + iowait;
    }

    public long totalBusy() {
        return user + nice + system + irq + softirq + steal + guest + guestNice;
    }

    public long total() {
        return totalIdle() + totalBusy();
    }

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
        // 间隔过短或计数未变时 dTotal<=0；返回 0% 以便进度条与文案仍刷新，避免界面「永远不更新」
        if (dTotal <= 0) {
            return new int[]{0, 0, 0};
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
