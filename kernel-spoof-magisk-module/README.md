# Violet Kernel Spoof (Minimal)

一个精简模块，只做两件事：

1. 伪装内核 `uname` 信息（版本 + 构建信息）
2. 开机自动应用伪装（`post-fs-data` + `service` 阶段各执行一次）

## 依赖

- 设备内核已支持 susfs `set_uname`
- 模块安装时会把内置 `tools/ksu_susfs_arm64` 覆盖到 `/data/adb/ksu/bin/ksu_susfs`

## 配置文件

- 持久化配置：`/data/adb/violet_kernel_spoof/config.sh`
- 默认内容：

```sh
# 0=关闭; 1=service 阶段应用; 2=post-fs-data 阶段应用
spoof_uname=1
kernel_version='default'
kernel_build='default'
```

## 修改伪装值

编辑 `/data/adb/violet_kernel_spoof/config.sh`：

```sh
spoof_uname=1
kernel_version='5.10.198-android13-9-gki'
kernel_build='#1 SMP PREEMPT Tue Apr 22 02:00:00 UTC 2026'
```

保存后重启，或手动执行：

```sh
/data/adb/ksu/bin/ksu_susfs set_uname '5.10.198-android13-9-gki' '#1 SMP PREEMPT Tue Apr 22 02:00:00 UTC 2026'
```

## 兼容性说明

- 如果 `ksu_susfs` 返回 `SUSFS operation not supported`，说明当前内核未启用 `set_uname` 能力。
- 这种情况无法靠模块脚本修复，只能更换支持该特性的内核/susfs 构建。

## 日志

- 日志文件：`/data/adb/violet_kernel_spoof/kernel_spoof.log`
