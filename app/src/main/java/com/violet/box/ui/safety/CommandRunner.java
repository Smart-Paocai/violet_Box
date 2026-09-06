package com.violet.box.ui.safety;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Draining a silent/hung child must never prevent the deadline check. */
final class CommandRunner {
    static final int MAX_OUTPUT_CHARS = 16384;
    private CommandRunner() { }

    static String[] run(String[] command, long timeoutMs) {
        if (timeoutMs <= 0) return new String[]{"124", "执行超时，结果待确认"};
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process child = process;
            Thread drain = new Thread(() -> {
                try (InputStreamReader reader = new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[1024];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        synchronized (output) {
                            int remaining = MAX_OUTPUT_CHARS - output.length();
                            if (remaining > 0) output.append(buffer, 0, Math.min(count, remaining));
                        }
                    }
                } catch (Exception ignored) { /* Timed-out child also closes its pipe. */ }
            }, "sensor-command-output");
            drain.setDaemon(true);
            drain.start();
            int code;
            for (;;) {
                try {
                    code = child.exitValue();
                    break;
                } catch (IllegalThreadStateException running) {
                    if (System.nanoTime() >= deadline) {
                        child.destroy();
                        return new String[]{"124", "执行超时，结果待确认"};
                    }
                    Thread.sleep(20);
                }
            }
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs > 0) drain.join(Math.min(remainingMs, 250));
            synchronized (output) {
                return new String[]{String.valueOf(code), output.toString()};
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new String[]{"125", "执行被中断，结果待确认"};
        } catch (Exception e) {
            return new String[]{"-1", e.getClass().getSimpleName() + ": " + e.getMessage()};
        } finally {
            if (process != null) process.destroy();
        }
    }
}
