# Mobile Sender

Android 手机投屏发送端：
- 扫描并选择局域网电视设备
- 发送视频链接到电视端播放（优先）
- 选择本地视频并发送（通过手机临时 HTTP 文件服务）
- 内置 H5 页面，支持网页视频投屏

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
