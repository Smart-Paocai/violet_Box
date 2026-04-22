# Violet Box Module

一个核心扩展模块，支持两类伪装：

1. 伪装内核 `uname` 信息（版本 + 构建信息）
2. 伪装全局机型属性（`resetprop` 覆盖 `ro.product.*` / `ro.build.*`）

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
spoof_props=1
ro_product_brand='REDMAGIC'
ro_product_manufacturer='nubia'
ro_product_model='NX799J'
ro_product_device='NX799J'
ro_build_fingerprint='REDMAGIC/NX799J/NX799J:16/BQ2A.250705.001/...:user/release-keys'
```

保存后重启，或手动执行：

```sh
/data/adb/ksu/bin/ksu_susfs set_uname '5.10.198-android13-9-gki' '#1 SMP PREEMPT Tue Apr 22 02:00:00 UTC 2026'
```

## 开机时机

- `spoof_uname=1` / `spoof_props=1`：在 `service` 阶段应用
- `spoof_uname=2` / `spoof_props=2`：在 `post-fs-data` 阶段应用
- `0` 表示关闭对应能力

## 兼容性说明

- 如果 `ksu_susfs` 返回 `SUSFS operation not supported`，说明当前内核未启用 `set_uname` 能力。
- `resetprop` 需由 Magisk/KSU/APatch 提供；若不可用，全局机型伪装不会生效。

## 日志

- 日志文件：`/data/adb/violet_kernel_spoof/kernel_spoof.log`
