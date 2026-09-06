package com.violet.box.ui.safety;

import android.os.SystemClock;

/** Shizuku UserService, executed as shell. Accepts only the sensorservice protocol. */
public class ShellService extends IShellService.Stub {
    public ShellService() { }

    @Override
    public String[] runCommand(String[] command, long deadlineElapsedMs) {
        if (!SensorCommand.valid(command)) return new String[]{"-1", "无效传感器命令"};
        return CommandRunner.run(command, Math.min(6000L, deadlineElapsedMs - SystemClock.elapsedRealtime()));
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
