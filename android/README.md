# LifeManga · Android

iOS 版的 Android 移植，**MVP 开发中**。

## 当前状态

- ✅ Compose 工程骨架（Kotlin 2.0 + AGP 8.6 + Compose BOM 2024.10）
- ✅ GitHub Actions 自动构建 Debug APK
- ⬜ API Key 设置 + Keychain 替代
- ⬜ gpt-image-2 单图生成
- ⬜ 后台任务（WorkManager + 前台通知）
- ⬜ 历史 / 详情页
- ⬜ 故事模式 / 角色库 / 任务面板（MVP 之后）

## 怎么拿 APK

每次 `push` 到 `main` 或手动触发 `Build Android APK` workflow 后：

1. 打开仓库 GitHub 页面 → **Actions** 标签
2. 点击最新一次 `Build Android APK` 运行
3. 滚到底部 **Artifacts** → 下载 `LifeManga-debug-apk.zip`
4. 解压得到 APK，发到手机安装（首次安装需要在「设置 → 应用 → 特殊权限 → 安装未知应用」开启来源）

## 本地开发（可选）

```bash
cd android
# 第一次需要本机有 Gradle 8.10+ 或 Android Studio
gradle assembleDebug
# 或用 Android Studio：File → Open → 选 android/ 目录
```

## 架构

参考 iOS 版（../LifeManga/）：

| 层 | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose |
| 状态 | @Published / ObservableObject | StateFlow / ViewModel |
| 持久化 | UserDefaults + JSON 文件 | DataStore + Room |
| API Key | Keychain | EncryptedSharedPreferences |
| 网络 | URLSession | Retrofit + OkHttp |
| 后台任务 | URLSessionConfiguration.background | WorkManager + Foreground Service |
| 图像加载 | UIImage | Coil |
