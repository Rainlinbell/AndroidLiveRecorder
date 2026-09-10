# 直播录制应用 (Live Stream Recorder)

这是一个功能强大的 Android 直播录制应用，支持多个主流直播平台，具有现代化的 UI 设计和丰富的功能特性。

## 功能特点

- **多平台支持**: 支持 Bilibili、Douyu、Huya、Douyin 等主流直播平台
- **实时监控**: 自动检测主播开播状态并开始录制
- **高质量录制**: 支持多种画质选择（原画、高清、标清、流畅）
- **QR码登录**: 支持通过二维码登录各大直播平台账号
- **智能管理**: 可设置特别关注主播，具备自动录制功能
- **用户友好**: 采用 Jetpack Compose 构建的现代化 UI
- **离线管理**: 支持查看和管理本地录制的历史文件

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM + Repository + Hilt 依赖注入
- **UI框架**: Jetpack Compose + Material3
- **数据库**: Room 本地数据库
- **网络**: OkHttp + Gson
- **后台服务**: Foreground Service
- **二维码**: ZXing 库

## 安装与使用

1. 克隆仓库到本地
2. 使用 Android Studio 打开项目
3. 同步 Gradle 依赖
4. 构建并安装到 Android 设备

## 项目结构

- `app/src/main/java/com/liverecorder/app/platform/` - 各平台适配器
- `app/src/main/java/com/liverecorder/app/service/` - 后台服务
- `app/src/main/java/com/liverecorder/app/ui/screens/` - UI 屏幕
- `app/src/main/java/com/liverecorder/app/recorder/` - 录制核心逻辑
- `app/src/main/java/com/liverecorder/app/data/` - 数据层

## 许可证

MIT License