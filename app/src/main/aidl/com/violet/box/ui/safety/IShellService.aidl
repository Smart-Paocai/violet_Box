package com.violet.box.ui.safety;

interface IShellService {
    /**
     * 以固定参数数组执行命令（服务运行在 shell 身份下），阻塞返回。
     * 返回 [退出码字符串, 合并后的 stdout+stderr]；异常时退出码为 -1。
     */
    String[] runCommand(in String[] cmd, long deadlineElapsedMs) = 0;

    /** Shizuku UserService 销毁回调（必须实现，服务退出用）。 */
    void destroy() = 16777114;
}
