package com.violet.safe.data.detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class AdvancedRuntimeDetector {

    public static boolean checkMountNamespace() {
        try {
            String selfMounts = readFile("/proc/self/mounts");
            String zygotePid = findZygotePid();
            if (zygotePid == null) return false;
            
            String zygoteMounts = readFile("/proc/" + zygotePid + "/mounts");
            return !selfMounts.equals(zygoteMounts);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> checkZygoteInjection() {
        List<String> found = new ArrayList<>();
        try {
            String zygotePid = findZygotePid();
            if (zygotePid == null) return found;

            String maps = readFile("/proc/" + zygotePid + "/maps").toLowerCase();
            for (String kw : HardcodedSignals.strongRuntimeKeywords) {
                if (maps.contains(kw.toLowerCase())) {
                    found.add("Zygote Injection: " + kw);
                }
            }
        } catch (Exception e) {}
        return found;
    }

    public static boolean checkKallsymsReadable() {
        try {
            File file = new File("/proc/kallsyms");
            return file.exists() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkProcessCapabilities() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CapEff:")) {
                    String value = line.split(":")[1].trim();
                    long caps = Long.parseUnsignedLong(value, 16);
                    long rootLevelCaps = 0x3fffffffffffffffL;
                    long dangerousCaps = 0x0000000000000001L |
                                         0x0000000000000002L |
                                         0x0000000000000004L |
                                         0x0000000000002000L |
                                         0x0000000000004000L |
                                         0x0000000000008000L |
                                         0x0000000000200000L;
                    return (caps & dangerousCaps) != 0L || caps >= rootLevelCaps;
                }
            }
            reader.close();
        } catch (Exception e) {}
        return false;
    }

    public static List<String> checkZygiskEnv() {
        List<String> found = new ArrayList<>();
        try {
            List<String> sensitiveKeys = new ArrayList<>(HardcodedSignals.envKeys);
            sensitiveKeys.add("JAVA_TOOL_OPTIONS");

            for (java.util.Map.Entry<String, String> entry : System.getenv().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                for (String sensitiveKey : sensitiveKeys) {
                    if (key.equalsIgnoreCase(sensitiveKey)) {
                        found.add("Suspicious Env Var: " + key + "=" + value);
                        break;
                    }
                }
            }
        } catch (Exception e) {}
        return found;
    }

    private static String findZygotePid() {
        try {
            File procDir = new File("/proc");
            File[] files = procDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.getName().matches("\\d+")) {
                        File cmdline = new File(file, "cmdline");
                        if (cmdline.exists()) {
                            String cmd = readFile(cmdline.getAbsolutePath());
                            if (cmd.contains("zygote")) {
                                return file.getName();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private static String readFile(String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
}
