<div align="center">

## 紫罗兰盒子 (VioletBox)

[![License: GPL 3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://github.com/Smart-Paocai/violet_Box/tree/main?tab=GPL-3.0-1-ov-file)
[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=flat-square&logo=telegram)](https://t.me/violettoolbox)
[![Release](https://img.shields.io/badge/Release-v1.1.0-success.svg?style=flat-square)](https://github.com/Smart-Paocai/violet_Box/releases)

**一款根据用户需求设计的 Android 玩机工具箱，我们后续将集成更多移动端的实用功能，为ROOT用户以及非ROOT用户提供更好的玩机体验！**


</div>

## ✨ 功能一览

| 功能 | 说明 |
| --- | --- |
| 🛡️ 摇一摇广告防护 | 无需ROOT，在激活Shizuku之后，通过限制目标应用传感器，阻止开屏摇一摇广告触发与跳转 |
| 🔄 SELinux 管理 | 查看 / 切换 SELinux 模式，支持开机自启 |
| 💾 分区管理 | 系统分区读取 / 写入 / 擦除 / 回读 |
| 🧬 字库备份 | 完整备份底层字库为 bin，可在 EDL / bootloader / 编程器刷写 |
| 📦 模块管理 | 批量刷入 Magisk / KernelSU / APatch 模块 |
| 📱 应用管理 | 提取 / 卸载 / 冻结 / 解冻应用 |
| 🆔 安卓 ID 修改 | 修改设备安卓 ID |
| 🎭 全局机型伪装 | resetprop 修改机型与构建指纹 |
| ☁️ Payload 云提取 | 在线下载 OTA 固件并提取指定分区镜像 |
| 🧩 紫罗兰插件 | 内核伪装 / TrickyStore扩展 / 隐藏应用列表配置 |

## 🛡️ 摇一摇广告防护（免 Root）

通过 [Shizuku](https://shizuku.rikka.app/zh-hans/) 以 shell 权限限制指定应用的加速度传感器，阻止开屏广告的摇一摇触发与跳转。

- **白名单式管理**：自主勾选需要防护的应用
- **免 Root**：需配合 Shizuku 使用（通过无线调试/PC端ADB命令启动，重启手机后需重新启动服务）
- ⚠️ 被限制的应用其摇一摇、体感等功能也会一并受影响，请按需开启

## 📥 下载

- 前往 [Releases](https://github.com/Smart-Paocai/violet_Box/releases) 下载最新版本
- 交流群组：[Telegram 频道](https://t.me/violettoolbox)

## 🛠️ 编译步骤

1. 克隆本项目到本地：
   ```bash
   git clone https://github.com/Smart-Paocai/violet_Box.git
   ```
2. 使用 Android Studio 打开项目。
3. 等待 Gradle 同步完成。
4. 点击运行（Run）或在终端执行以下命令进行编译：
   ```bash
   ./gradlew :app:assembleDebug
   ```

---

## ⚠️ 免责声明

本程序仅供学习交流，所有功能均为正常玩机功能，请勿用于非法用途，程序所有功能不针对任何商业项目，非法使用造成的任何后果均与本程序无关!

---

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 开源许可证。
