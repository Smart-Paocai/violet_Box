package com.violet.box.ui.safety;

import org.junit.Test;
import static org.junit.Assert.*;

public class SensorCommandTest {
    @Test public void targetsTheApplicationsUserForAllOperations() {
        int user = SensorCommand.userId(1012345);
        assertEquals(10, user);
        for (String operation : new String[]{"get-uid-state", "set-uid-state", "reset-uid-state"}) {
            String[] command = SensorCommand.build(operation, "com.example.app", user);
            assertEquals("10", command[command.length - 1]);
            assertTrue(SensorCommand.valid(command));
        }
    }

    @Test public void rejectsOtherProgramsArgumentsAndMalformedPackages() {
        assertFalse(SensorCommand.valid(new String[]{"sh", "-c", "echo test"}));
        String[] command = SensorCommand.build("set-uid-state", "com.example.app", 0);
        command[0] = "other";
        assertFalse(SensorCommand.valid(command));
        command[0] = "cmd";
        command[4] = "active";
        assertFalse(SensorCommand.valid(command));
        command[4] = "idle";
        command[3] = "com.example; echo test";
        assertFalse(SensorCommand.valid(command));
        command[3] = "com.example.app";
        command[6] = "-1";
        assertFalse(SensorCommand.valid(command));
    }

    @Test public void shizukuDestroyUsesRequiredBinderTransaction() throws Exception {
        java.lang.reflect.Field field = IShellService.Stub.class.getDeclaredField("TRANSACTION_destroy");
        field.setAccessible(true);
        assertEquals(16777115, field.getInt(null));
    }

    @Test public void batchStopsForTimeoutOrDisconnectionButNotAnOrdinaryCommandError() {
        assertTrue(new SafetyShell.Result(false, 124, "timeout", "").isTransportFailure());
        assertTrue(new SafetyShell.Result(false, -1, "", "disconnected").isTransportFailure());
        assertFalse(new SafetyShell.Result(false, 1, "package error", "").isTransportFailure());
        assertFalse(new SafetyShell.Result(true, 0, "", "").isTransportFailure());
    }
}
