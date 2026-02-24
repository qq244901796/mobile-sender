# Mobile Sender 2.0

Android 手机投屏发送端（协议化架构）：
- 统一扫描设备：自定义协议设备 + DLNA 普通电视
- 发送视频链接到电视播放（优先）
- 选择本地视频并发送（手机临时 HTTP 文件服务）
- 内置 H5 页面，支持网页视频投屏
- 投后控制：播放 / 暂停 / 退出播放（主页面与 WebCast 页面都支持）

## 协议支持
- `CUSTOM`：对接本项目 `tv-receiver`，完整控制
- `DLNA`：对接普通智能电视（需电视支持 DLNA/UPnP）

## 控制能力说明
- `CUSTOM`：播放、暂停、停止（退出播放）
- `DLNA`：播放、暂停、停止（兼容性依电视实现）

## 环境要求
- Android Studio
- JDK 17
- Android SDK 34

## 运行
```bash
./gradlew assembleDebug
```

## Release APK
路径：
- `app/build/outputs/apk/release/app-release.apk`

## 配套项目
电视接收端仓库：
- `https://github.com/qq244901796/tv-receiver`
