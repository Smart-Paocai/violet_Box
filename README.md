<div align="center">

## 紫罗兰盒子 (VioletBox)

[![License: GPL 3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://github.com/Smart-Paocai/violet_Box/tree/main?tab=GPL-3.0-1-ov-file)
[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=flat-square&logo=telegram)](https://t.me/violettoolbox)
[![Release](https://img.shields.io/badge/Release-v1.1.0-success.svg?style=flat-square)](https://github.com/Smart-Paocai/violet_Box/releases)

**一款根据用户需求设计的 Android 玩机工具箱**

摇一摇广告防护 · 系统级定制 · Root 工具集 · Material 3 界面 · 支持深色模式

</div>

## ✨ 功能一览

| 功能 | 说明 |
| --- | --- |
| 🛡️ 摇一摇广告防护 | **免 Root**，限制目标应用传感器，阻止开屏摇一摇广告触发与跳转 |
| 📋 设备信息 | 设备信息一览，支持单击复制 |
| 🔄 SELinux 管理 | 查看 / 切换 SELinux 模式，支持开机自启 |
| 💾 分区管理 | 系统分区读取 / 写入 / 擦除 / 回读 |
| 🧬 字库备份 | 完整备份底层字库为 bin，可在 EDL / bootloader / 编程器刷写 |
| 📦 模块管理 | 批量刷入 Magisk / KernelSU / APatch 模块 |
| 📱 应用管理 | 提取 / 卸载 / 冻结 / 解冻应用 |
| 🆔 安卓 ID 修改 | 修改设备安卓 ID |
| 🎭 全局机型伪装 | resetprop 修改机型与构建指纹（需核心扩展模块） |
| ☁️ Payload 云提取 | 在线下载 OTA 固件并提取分区镜像 |
| 🧩 紫罗兰插件 | 内核伪装 / TrickyStore 配套 / 隐藏应用列表配置 |

## 🛡️ 摇一摇广告防护（免 Root）

通过 [Shizuku](https://shizuku.rikka.app/zh-hans/) 以 shell 权限限制指定应用的加速度传感器，阻止开屏广告的摇一摇触发与跳转。

- **白名单式管理**：自主勾选需要防护的应用，支持单个恢复或全部恢复
- **默认仅列用户应用**，可按需扩展显示系统应用
- **免 Root**：需配合 Shizuku 使用（通过无线调试启动，重启手机后需重新启动服务）
- ⚠️ 被限制的应用其摇一摇、体感等功能也会一并受影响，请按需开启

## 📥 下载

- 前往 [Releases](https://github.com/Smart-Paocai/violet_Box/releases) 下载最新版本
- 更新动态与交流：[Telegram 频道](https://t.me/violettoolbox)

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

1. 本工具涉及大量系统底层修改功能（如分区管理、内核伪装、SELinux 切换等）。**请在完全了解操作后果的前提下使用！**
2. 不当的操作可能导致设备无法启动（变砖）、数据丢失或失去保修。作者不对任何因使用本软件造成的设备损坏负责。
3. 伪装功能仅供安全研究与测试使用，请勿用于非法用途。

---

## 🤝 参与贡献

欢迎任何形式的贡献！如果你有好的想法、修复了 Bug 或是改进了翻译，请随时提交 Pull Request 或发起 Issue。

---

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 开源许可证。
