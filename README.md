<div align="center">

## 紫罗兰Box (VioletBox)
[![License: GPL 3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://github.com/Smart-Paocai/violet_Box/tree/main?tab=GPL-3.0-1-ov-file)
[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=flat-square&logo=telegram)](https://t.me/violet_Box)
[![Release](https://img.shields.io/badge/Release-v1.0.0-success.svg?style=flat-square)](https://github.com/Smart-Paocai/violet_Box/releases/tag/v1.0.0)

</div>

## 简介

紫罗兰Box 是一款Android玩机工具箱。我们致力于依据每一位玩机用户的需求去制作完善软件功能，希望能打造最受欢迎的移动端玩机工具箱。

---

## 核心特性

### 设备与信息
- **全面设备识别**：识别设备的创建信息，并支持单击复制到剪切板

### ROOT检测
- 支持针对主流 Root 框架、Magisk、KernelSU 及各类隐藏模块的深度探查。
- 检测 Bootloader 锁状态、USB 调试状态、SELinux 状态（Enforcing/Permissive）以及 ro.secure 等核心系统属性。
- 一键排查设备中存在的敏感或风险应用。

### 玩机与定制
紫罗兰Box 提供了极客用户最爱的系统级操作功能，包括但不限于：
- **SELinux管理**：轻松切换或查看当前系统的 SELinux模式，并支持设置开机自启。
- **分区管理**：可以对系统分区进行读/写/擦/回读操作，实现和电脑端同样的效果。
- **字库备份**：可以对设备底层字库进行完整备份，生成可以在EDL&bootloader&编程器自由刷写的bin格式文件
- **模块管理**：可以批量刷入Magisk/KernelSU/Apatch的模块。
- **应用管理**：可以读取设备内的安装包，对安装包进行提取/卸载/冻结/解冻操作。
- **安卓ID修改**：修改设备的安卓ID
- **全局机型伪装**：
- **Payload云提取**

### 紫罗兰插件 (Violet Plugins)
提供一系列进阶的定制与隐藏ROOT功能
- **内核伪装** （无需SUSFUS模块对内核版本&构建日期的伪装）
- **TrickyStore 系列组件** (复刻隐藏BL列表、设置安全补丁& boot Hash 自定义)
- **隐藏应用列表**  
### 编译步骤
1. 克隆本项目到本地：
   ```bash
   git clone https://github.com/your-username/VioletDetection.git
   ```
2. 使用 Android Studio 打开项目。
3. 等待 Gradle 同步完成。
4. 点击运行（Run）或在终端执行以下命令进行编译：
   ```bash
   ./gradlew :app:assembleDebug
   ```

---

## 免责声明

1. 本工具涉及大量系统底层修改功能（如分区管理、内核伪装、SELinux 切换等）。**请在完全了解操作后果的前提下使用！**
2. 不当的操作可能导致设备无法启动（变砖）、数据丢失或失去保修。作者不对任何因使用本软件造成的设备损坏负责。
3. 伪装功能仅供安全研究与测试使用，请勿用于非法用途。

---

## 参与贡献

欢迎任何形式的贡献！如果你有好的想法、修复了 Bug 或是改进了翻译，请随时提交 Pull Request 或发起 Issue。

---

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 开源许可证。
