<div align="center">

# 🔮 紫罗兰Box (VioletBox)

**一款专注设备环境检测与系统深度定制的综合性玩机工具**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://github.com/Smart-Paocai/violet_Box/tree/main?tab=GPL-3.0-1-ov-file)
[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=flat-square&logo=telegram)](https://t.me/violet_Box)
[![Release](https://img.shields.io/badge/Release-v1.0.0-success.svg?style=flat-square)](https://github.com/Smart-Paocai/violet_Box/releases/tag/v1.0.0)

</div>

## 📖 简介

紫罗兰Box 是一款针对 Android 极客用户设计的深度定制和设备检测工具箱。我们致力于提供一站式的系统级操作体验，包含设备硬件/软件信息查看、全方位的安全与 Root 环境检测、以及丰富强大的“玩机”定制功能。

不仅功能硬核，紫罗兰Box 在 UI 设计上也毫不妥协，项目中深度融合了 Apple 风格的设计语言，特别引入了基于 Jetpack Compose 构建的高级悬浮毛玻璃底栏，带来丝滑流畅的物理阻尼动画和沉浸式视觉体验。

---

## ✨ 核心特性

### 📱 设备与信息 (Device Info)
- **全面设备识别**：快速获取详尽的设备型号、系统版本、构建指纹等基础信息。
- **系统状态监控**：实时查看 CPU 核心状态（频率/负载）、GPU 负载，并通过可视化图表直观展示。

### 🛡️ 安全与检测 (Security & Detection)
- **硬核 Root 检测**：集成 `RootBeer` 等检测方案，支持针对主流 Root 框架、Magisk、KernelSU 及各类隐藏模块的深度探查。
- **运行环境评估**：检测 Bootloader 锁状态、USB 调试状态、SELinux 状态（Enforcing/Permissive）以及 ro.secure 等核心系统属性。
- **风险应用扫描**：一键排查设备中存在的敏感或风险应用。

### 🛠️ 玩机与定制 (Explore & Tweaks)
紫罗兰Box 提供了极客用户最爱的系统级操作功能，包括但不限于：
- **SELinux 管理器**：轻松切换或查看当前系统的 SELinux 策略。
- **分区管理器 (Partition Manager)**：直接对系统底层分区进行管理和查看。
- **字体库备份 (Font Backup)**：一键备份与管理系统字体文件，以防定制翻车。
- **模块管理器 (Module Manager)**：对系统级修改模块进行统一管理。
- **应用管理器 (App Manager)**：超越原生设置，支持提取 APK、卸载系统应用、Root 冻结与备份。

### 🧩 紫罗兰插件 (Violet Plugins)
提供一系列进阶的定制与欺骗功能（需 Root 或特定环境支持）：
- **设备标识修改** (Device ID Modify)
- **全局机型伪装** (Global Device Spoof)
- **内核伪装** (Kernel Disguise)
- **TrickyStore 系列组件** (支持 Keybox 补丁、应用列表及 Hash 定制)
- **隐藏应用列表配置** (Hidden AppList Config)
- **Payload 核心操作** (Payload Dumper/Flasher)

### 🎨 极致的 UI 体验
- **Apple 风格毛玻璃悬浮底栏**：使用 `Jetpack Compose` 与 `kyant.backdrop` 实时渲染底层背景，呈现高质量的高斯模糊（Blur）、材质共振（Vibrancy）和镜头透射（Lens）效果。
- **物理阻尼动画**：底栏滑动时附带微弱拉伸形变与弹簧阻尼反馈，媲美 iOS 交互。
- **丝滑左右滑屏**：各功能页面之间支持流畅无堆叠的 100% 全屏硬件加速滑动切换。

---

## 🏗️ 架构与技术栈

- **开发语言**: Java / Kotlin 混合开发
- **UI 框架**: 传统的 `XML + CoordinatorLayout` 结合最新的 `Jetpack Compose` (混合渲染架构)
- **核心依赖**:
  - `com.kyant.backdrop`: 实现高性能毛玻璃特效
  - `com.scottyab:rootbeer-lib`: 用于深度 Root 检测
  - `OkHttp3`, `Apache Commons Compress`, `XZ`: 用于底层文件下载与 Payload 解包操作

---

## 🚀 编译与运行

### 环境要求
- Android Studio Iguana (2023.2.1) 或更高版本
- JDK 11+
- 目标设备系统：Android 7.0 (API 24) 及以上

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
