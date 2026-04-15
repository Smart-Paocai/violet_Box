package com.violet.safe.detector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class RootDetector {

    private final Context context;

    public RootDetector(Context context) {
        this.context = context;
    }

    public boolean isDeviceRooted() {
        return !checkSuBinaries().isEmpty() ||
               !checkRootPackages().isEmpty() ||
               !checkMagiskPaths().isEmpty() ||
               !checkDangerousBinaries().isEmpty() ||
               !checkHideBypassModules().isEmpty() ||
               !checkMountPoints().isEmpty() ||
               !checkDangerousProperties().isEmpty() ||
               !checkRuntimeArtifacts().isEmpty() ||
               !checkTmpfsOnData().isEmpty() ||
               !checkSuTimestamps().isEmpty() ||
               !checkLineageOS().isEmpty() ||
               !checkCustomRom().isEmpty() ||
               !checkBuildFieldCoherence().isEmpty() ||
               !checkAdvancedRuntime().isEmpty() ||
               !checkOverlayFS().isEmpty();
    }

    public List<String> checkSuBinaries() {
        List<String> found = new ArrayList<>();
        for (String path : HardcodedSignals.suPaths) {
            if (new File(path).exists()) found.add(path);
        }
        return found;
    }

    public List<String> checkRootPackages() {
        List<String> found = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<String> allPkgs = new ArrayList<>();
        allPkgs.addAll(HardcodedSignals.rootPackages);
        allPkgs.addAll(HardcodedSignals.kernelSuPackages);
        allPkgs.addAll(HardcodedSignals.patchedApps);
        
        for (String pkg : allPkgs) {
            try {
                pm.getPackageInfo(pkg, 0);
                found.add(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
        }
        return found;
    }

    public List<String> checkMagiskPaths() {
        List<String> found = new ArrayList<>();
        List<String> allPaths = new ArrayList<>();
        allPaths.addAll(HardcodedSignals.magiskPaths);
        allPaths.addAll(HardcodedSignals.kernelSuPaths);
        allPaths.addAll(HardcodedSignals.rootPaths);
        
        for (String path : allPaths) {
            if (path.equals("/debug_ramdisk") || path.equals("/debug_ramdisk/")) continue;
            File f = new File(path);
            if (f.exists() && f.canRead()) found.add(path);
        }
        return found;
    }

    public List<String> checkDangerousBinaries() {
        List<String> found = new ArrayList<>();
        List<String> allPaths = new ArrayList<>();
        allPaths.addAll(HardcodedSignals.dangerousBinaries);
        allPaths.addAll(HardcodedSignals.binaryPaths);
        
        for (String path : allPaths) {
            if (path.equals("/debug_ramdisk") || path.equals("/debug_ramdisk/")) continue;
            File f = new File(path);
            if (f.exists() && f.canRead()) found.add(path);
        }
        return found;
    }

    public List<String> checkHideBypassModules() {
        List<String> found = new ArrayList<>();
        List<String> keywords = HardcodedSignals.hideBypassKeywords;
        List<String> scanFiles = new ArrayList<>(HardcodedSignals.moduleScanFiles);
        scanFiles.addAll(Arrays.asList("action.sh", "system.prop"));

        for (String dirPath : HardcodedSignals.moduleDirs) {
            File dir = new File(dirPath);
            File[] modules = dir.listFiles();
            if (modules != null) {
                for (File module : modules) {
                    String name = module.getName().toLowerCase();
                    String normalizedName = name.replaceAll("[^a-z0-9]", "");
                    for (String key : keywords) {
                        String normalizedKey = key.replaceAll("[^a-z0-9]", "");
                        if (name.contains(key) || normalizedName.contains(normalizedKey)) {
                            found.add("Hidden module found: " + module.getName() + " @ " + dirPath);
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }

    public List<String> checkMountPoints() {
        List<String> found = new ArrayList<>();
        String[] paths = {"/proc/mounts", "/proc/1/mountinfo"};
        List<String> keywords = new ArrayList<>(HardcodedSignals.rootKeywords);
        keywords.addAll(Arrays.asList("magisk", "zygisk", "kernelsu", "ksu", "apatch", "shamiko", "trickystore", "playintegrityfix", "susfs"));

        for (String path : paths) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("cat " + path).getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase();
                    for (String kw : keywords) {
                        if (lower.contains(kw) && (lower.contains("/data/adb") || lower.contains("/debug_ramdisk") || lower.contains("/.magisk") || lower.contains("/sbin") || lower.contains("overlay"))) {
                            found.add("Suspicious mount: " + line.trim());
                            break;
                        }
                    }
                }
                reader.close();
            } catch (Exception e) { }
        }
        return found;
    }

    public List<String> checkDangerousProperties() {
        List<String> found = new ArrayList<>();
        
        // Check exact props
        for (Map.Entry<String, String> entry : HardcodedSignals.dangerousRootProps.entrySet()) {
            String value = getProp(entry.getKey());
            if (!value.isEmpty() && value.equals(entry.getValue())) {
                found.add("Dangerous Prop: " + entry.getKey() + "=" + value);
            }
        }
        for (Map.Entry<String, String> entry : HardcodedSignals.spoofedBootProps.entrySet()) {
            String value = getProp(entry.getKey());
            if (!value.isEmpty() && value.equals(entry.getValue())) {
                found.add("Spoofed Boot Prop: " + entry.getKey() + "=" + value);
            }
        }
        for (String prop : HardcodedSignals.kernelSuProps) {
            String value = getProp(prop);
            if (!value.isEmpty()) found.add("KernelSU Prop: " + prop + "=" + value);
        }
        return found;
    }

    public List<String> checkRuntimeArtifacts() {
        List<String> found = new ArrayList<>();
        List<String> keywords = HardcodedSignals.allFrameworkSweepKeywords;
        
        try {
            String[] paths = {"/proc/self/maps", "/proc/1/maps"};
            for (String path : paths) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("cat " + path).getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase();
                    for (String kw : keywords) {
                        if (lower.contains(kw) && (lower.contains("/data/adb") || lower.contains("/debug_ramdisk") || lower.contains("/sbin") || lower.contains("memfd:") || lower.contains("(deleted)"))) {
                            found.add("Runtime artifact in maps: " + line.trim());
                            break;
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception e) {}
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("cat /proc/net/unix").getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String kw : keywords) {
                    if (lower.contains(kw) && (lower.contains("@") || lower.contains("/dev/") || lower.contains("socket"))) {
                        found.add("Runtime artifact in unix sockets: " + line.trim());
                        break;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {}
        return found;
    }

    public List<String> checkTmpfsOnData() {
        List<String> found = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("cat /proc/mounts").getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    String mountPoint = parts[1];
                    String fs = parts[2];
                    if ("tmpfs".equals(fs) && (mountPoint.startsWith("/data/adb") || mountPoint.equals("/debug_ramdisk") || mountPoint.startsWith("/sbin"))) {
                        found.add("tmpfs on data path: " + mountPoint);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {}
        return found;
    }

    public List<String> checkSuTimestamps() {
        List<String> found = new ArrayList<>();
        long recentThresholdMs = 30L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        String[] paths = {"/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap", "/data/adb/modules", "/debug_ramdisk"};
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                long age = now - f.lastModified();
                if (age < recentThresholdMs && f.lastModified() > 0) {
                    found.add("Recent root artifact modification: " + path);
                }
            }
        }
        return found;
    }

    public List<String> checkLineageOS() {
        List<String> found = new ArrayList<>();
        // Init Files
        String[] initFiles = {
            "/system_ext/etc/init/init.lineage-system_ext.rc",
            "/system/etc/init/init.lineage-system.rc",
            "/system/system_ext/etc/init/init.lineage-system_ext.rc",
            "/product/etc/init/init.lineage-service.rc",
            "/vendor/etc/init/init.lineage-vendor.rc",
            "/system_ext/etc/permissions/org.lineageos.platform.xml",
            "/system/etc/permissions/org.lineageos.platform.xml",
            "/system_ext/framework/org.lineageos.platform.jar",
            "/system/framework/org.lineageos.platform.jar"
        };
        for (String path : initFiles) {
            if (new File(path).exists()) found.add("LineageOS init file: " + path);
        }
        // Permissions
        PackageManager pm = context.getPackageManager();
        String[] permissions = {
            "lineageos.permission.HARDWARE_ABSTRACTION_ACCESS",
            "lineageos.permission.MANAGE_LIVEDISPLAY",
            "lineageos.permission.WRITE_SETTINGS",
            "org.lineageos.permission.WRITE_SETTINGS"
        };
        for (String perm : permissions) {
            try {
                pm.getPermissionInfo(perm, 0);
                found.add("LineageOS permission: " + perm);
            } catch (Exception e) {}
        }
        return found;
    }

    public List<String> checkCustomRom() {
        List<String> found = new ArrayList<>();
        for (String path : HardcodedSignals.customRomFiles) {
            if (new File(path).exists()) found.add("Custom ROM file: " + path);
        }
        for (Map.Entry<String, String> entry : HardcodedSignals.customRomProps.entrySet()) {
            String val = getProp(entry.getKey());
            if (!val.isEmpty()) found.add("Custom ROM prop: " + entry.getKey() + "=" + val);
        }
        return found;
    }

    public List<String> checkBuildFieldCoherence() {
        List<String> found = new ArrayList<>();
        String propFingerprint = getProp("ro.build.fingerprint");
        String propTags = getProp("ro.build.tags");
        String propType = getProp("ro.build.type");
        String runtimeFingerprint = Build.FINGERPRINT;

        if (runtimeFingerprint != null && !runtimeFingerprint.isEmpty() && !propFingerprint.isEmpty()) {
            if (!runtimeFingerprint.equals(propFingerprint)) {
                found.add("Build.FINGERPRINT differs from getprop ro.build.fingerprint");
            }
        }
        if (Build.TAGS != null && !Build.TAGS.isEmpty() && !propTags.isEmpty() && !Build.TAGS.equals(propTags)) {
            found.add("Build.TAGS differs from ro.build.tags");
        }
        if (Build.TYPE != null && !Build.TYPE.isEmpty() && !propType.isEmpty() && !Build.TYPE.equals(propType)) {
            found.add("Build.TYPE differs from ro.build.type");
        }
        return found;
    }

    public List<String> checkAdvancedRuntime() {
        List<String> found = new ArrayList<>();
        if (AdvancedRuntimeDetector.checkMountNamespace()) found.add("Mount Namespace diverges from zygote");
        found.addAll(AdvancedRuntimeDetector.checkZygoteInjection());
        if (AdvancedRuntimeDetector.checkKallsymsReadable()) found.add("/proc/kallsyms is readable");
        if (AdvancedRuntimeDetector.checkProcessCapabilities()) found.add("Process has dangerous or root capabilities");
        found.addAll(AdvancedRuntimeDetector.checkZygiskEnv());
        return found;
    }

    public List<String> checkApkInstallSource() {
        List<String> found = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            String installer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installer = pm.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName();
            } else {
                installer = pm.getInstallerPackageName(context.getPackageName());
            }
            Set<String> knownStores = new HashSet<>(Arrays.asList(
                "com.android.vending",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
                "com.huawei.appmarket", "com.xiaomi.market"
            ));
            if (installer == null) {
                found.add("APK installed via ADB or unknown source");
            } else if (!knownStores.contains(installer)) {
                found.add("Installed by unknown app store: " + installer);
            }
        } catch (Exception e) {}
        return found;
    }

    public List<String> checkOverlayFS() {
        List<String> found = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("cat /proc/mounts").getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 4) {
                    String mountPoint = parts[1];
                    String fs = parts[2];
                    String options = parts[3];
                    for (String protectedPath : HardcodedSignals.protectedSystemPaths) {
                        if (mountPoint.startsWith(protectedPath)) {
                            boolean isOverlay = fs.equals("overlay");
                            boolean isTmpfs = fs.equals("tmpfs");
                            String lowerLine = line.toLowerCase();
                            boolean hasSuspiciousKeywords = lowerLine.contains("magisk") || lowerLine.contains("core/mirror") || lowerLine.contains("ksu") || lowerLine.contains("apatch") || lowerLine.contains("adb");
                            
                            // Only flag if it's explicitly overlay with root traces, or if it has typical magisk loop/adb options
                            if ((isOverlay && hasSuspiciousKeywords) || (isTmpfs && (options.contains("loop") || lowerLine.contains("adb")))) {
                                found.add("OverlayFS/Tmpfs modification on system path: " + mountPoint + " [" + fs + "]");
                                break;
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {}
        return found;
    }

    private String getProp(String name) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
