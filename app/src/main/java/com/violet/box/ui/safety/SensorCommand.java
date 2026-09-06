package com.violet.box.ui.safety;

/** Fixed sensorservice protocol. User IDs always belong to the target application. */
final class SensorCommand {
    private SensorCommand() { }

    static int userId(int uid) {
        if (uid < 0) throw new IllegalArgumentException("无效 UID");
        return uid / 100000;
    }

    static String[] build(String operation, String pkg, int userId) {
        if (pkg == null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
                || userId < 0) throw new IllegalArgumentException("无效应用或用户");
        if ("set-uid-state".equals(operation)) {
            return new String[]{"cmd", "sensorservice", operation, pkg, "idle", "--user", String.valueOf(userId)};
        }
        if (!"get-uid-state".equals(operation) && !"reset-uid-state".equals(operation)) {
            throw new IllegalArgumentException("不支持的操作");
        }
        return new String[]{"cmd", "sensorservice", operation, pkg, "--user", String.valueOf(userId)};
    }

    static boolean valid(String[] command) {
        if (command == null || (command.length != 6 && command.length != 7)) return false;
        try {
            int userId = Integer.parseInt(command[command.length - 1]);
            return java.util.Arrays.equals(command, build(command[2], command[3], userId));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
