package com.violet.box.ui.safety;

import org.junit.Test;
import static org.junit.Assert.*;

public class SensorSupportTest {
    private SafetyShell.SupportCheck classify(int code, String output, String error) {
        return SafetyShell.classifyCommandSupport(new SafetyShell.Result(code == 0, code, output, error));
    }

    @Test public void aospPrintfReturnCodesEnableSupportedDevices() {
        assertEquals(SafetyShell.Support.SUPPORTED, classify(5, "idle\n", "").support);
        assertEquals(SafetyShell.Support.SUPPORTED, classify(7, "active\n", "").support);
        assertEquals("", classify(5, "idle\n", "").detail);
    }

    @Test public void zeroExitRomVariantsRemainSupported() {
        assertEquals(SafetyShell.Support.SUPPORTED, classify(0, "idle\n", "").support);
        assertEquals(SafetyShell.Support.SUPPORTED, classify(0, "active\r\n", "").support);
    }

    @Test public void stateWordsDoNotHidePermissionErrorsOrMalformedOutput() {
        assertEquals(SafetyShell.Support.NO_PERMISSION,
                classify(1, "Permission denied: get-uid-state active", "").support);
        assertEquals(SafetyShell.Support.UNKNOWN, classify(5, "idle\nextra output", "").support);
        assertEquals(SafetyShell.Support.UNKNOWN, classify(7, "idle\n", "").support);
        assertEquals(SafetyShell.Support.UNKNOWN, classify(-1, "active\n", "disconnected").support);
        assertEquals(SafetyShell.Support.UNKNOWN, classify(0, "", "").support);
    }

    @Test public void detectionFailuresHaveActionableBoundedDetails() {
        SafetyShell.SupportCheck disconnected = classify(-1, "", "连接 Shizuku 服务超时，可重新检测");
        assertTrue(disconnected.detail.contains("连接 Shizuku 服务超时"));
        assertTrue(disconnected.detail.contains("exit=-1"));
        assertEquals(SafetyShell.Support.NOT_SUPPORTED,
                classify(20, "Can't find service: sensorservice", "").support);
        assertTrue(classify(1, String.join("", java.util.Collections.nCopies(1000, "x")), "").detail.length() < 300);
    }

    @Test public void queryCompatibilityDoesNotRelaxMutationSuccessRules() {
        SafetyShell.Result nonzero = new SafetyShell.Result(false, 5, "idle\n", "");
        assertEquals(SafetyShell.Support.SUPPORTED, SafetyShell.classifyCommandSupport(nonzero).support);
        assertFalse(nonzero.ok);
    }
}
