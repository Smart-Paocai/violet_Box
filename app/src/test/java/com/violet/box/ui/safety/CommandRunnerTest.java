package com.violet.box.ui.safety;

import org.junit.Test;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class CommandRunnerTest {
    private String[] child(String mode) throws Exception {
        String binary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classes = new File(CommandRunnerTestChild.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
        return new String[]{new File(System.getProperty("java.home"), "bin/" + binary).getPath(),
                "-cp", classes, CommandRunnerTestChild.class.getName(), mode};
    }

    @Test public void silentProcessCannotBlockDeadline() throws Exception {
        long start = System.nanoTime();
        String[] result = CommandRunner.run(child("sleep"), 250);
        assertEquals("124", result[0]);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
    }

    @Test public void outputIsDrainedAndCappedAndExitCodePreserved() throws Exception {
        String[] result = CommandRunner.run(child("output"), 6000);
        assertEquals("17", result[0]);
        assertTrue(result[1].startsWith("stderr:"));
        assertEquals(CommandRunner.MAX_OUTPUT_CHARS, result[1].length());
    }

    @Test public void expiredRequestDoesNotStartProcess() {
        assertEquals("124", CommandRunner.run(new String[]{"nonexistent-command"}, 0)[0]);
    }

    @Test public void realProcessWithAospQueryExitCodeIsRecognizedAsSupported() throws Exception {
        String[] response = CommandRunner.run(child("idle-query"), 3000);
        int code = Integer.parseInt(response[0]);
        assertEquals(5, code);
        SafetyShell.Result result = new SafetyShell.Result(code == 0, code, response[1], "");
        assertEquals(SafetyShell.Support.SUPPORTED, SafetyShell.classifyCommandSupport(result).support);
    }

    @Test public void interruptionCancelsWaitAndPreservesInterruptFlag() throws Exception {
        AtomicReference<String[]> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        String[] command = child("sleep");
        Thread worker = new Thread(() -> {
            result.set(CommandRunner.run(command, 10000));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(100);
        worker.interrupt();
        worker.join(3000);
        assertFalse(worker.isAlive());
        assertEquals("125", result.get()[0]);
        assertTrue(interrupted.get());
    }
}
