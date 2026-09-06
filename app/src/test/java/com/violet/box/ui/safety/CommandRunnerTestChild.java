package com.violet.box.ui.safety;

/** Subprocess fixture: real pipe/process behavior without an Android device or sensor changes. */
public class CommandRunnerTestChild {
    public static void main(String[] args) throws Exception {
        if ("sleep".equals(args[0])) {
            Thread.sleep(10000);
        } else if ("idle-query".equals(args[0])) {
            System.out.print("idle\n");
            System.exit(5);
        } else {
            System.err.print("stderr:");
            for (int i = 0; i < 100000; i++) System.out.print('x');
            System.exit(17);
        }
    }
}
