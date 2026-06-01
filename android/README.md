# LifeManga · Android 模块

仓库根目录的 [../README.md](../README.md) 是项目主 README；这份只讲 Android 模块本身——怎么开、怎么改、目录怎么读。

---

## 当前状态

| 模块 | 文件 | 状态 |
|---|---|---|
| 创作页 | `ui/create/CreateScreen.kt` | ✅ |
| 历史页 | `ui/history/HistoryScreen.kt` | ✅ |
| 作品详情 | `ui/detail/DetailScreen.kt` | ✅ |
| 设置页 | `ui/settings/SettingsScreen.kt` | ✅ |
| 工程列表 | `ui/projects/ProjectListScreen.kt` | ✅ 列表 + 切换 |
| 角色库 | `ui/characters/CharacterLibraryScreen.kt` | ✅ 列表 + 创建 |
| 故事模式（Qwen） | `work/StoryScriptWorker.kt` | ✅ 剧本生成；UI 暂未串到创作页 |
| 后台任务 | `work/MangaGenerationWorker.kt` | ✅ WorkManager + 前台服务 |
| 多后端支持 | `network/ComfyUIClient.kt` `network/OpenAIClient.kt` `network/QwenClient.kt` | ⚠️ 创作流只走 ComfyUI，OpenAI/Azure 代码留存未启用 |
| 角色载入到创作页 | — | ❌ 角色生成完跳回 library |
| 「续接前一张」 | — | ❌ 列表可看，无续接 UI |

> 「状态」对应 `ui/` 下的实际屏幕 + `work/` 下的实际 Worker，**不靠 TODO 列表**。要新增功能先看现状再决定。

---

## 拿 APK

主 README 的"跑起来"已经讲过。再贴一遍本地命令：

```bash
# 仓库根
cd LifeManga
# 任选其一
./android/gradlew -p android assembleDebug      # 用 wrapper（推荐先在 android/ 跑 gradle wrapper）
# 或
cd android && gradle assembleDebug              # 装好 Gradle 8.10+ 的情况
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。

**CI 路径**：`.github/workflows/build-apk.yml` 在 push 到 `android/**` 时自动跑，artifact 名为 `LifeManga-debug-apk`，保留 30 天。

---

## 本地开发

### 环境

- **JDK 17**（`temurin` 发行版）
- **Android SDK**：API 34（compileSdk / targetSdk），最低 26（minSdk）
- **Gradle 8.10.2**（wrapper 已配）
- **Android Studio Hedgehog (2023.1.1) 或更新**

### 打开

1. Android Studio → File → Open → 选仓库里的 `android/` 目录（**不是**仓库根）
2. 第一次 sync 会下 Kotlin 2.0.21 / Compose BOM 2024.10.01 / Room 2.6.1 / WorkManager 2.9.1 等依赖
3. 选个模拟器或真机，点运行

### 跑测试

目前没写单元测试（`app/src/test/` 和 `androidTest/` 都是空的）。手动测一遍的最小路径：

```
启动 → 设置 → 保持默认 ComfyUI URL 不动 → 保存 → 返回创作页
→ 选 1~6 张图（相册）→ 选风格 + 彩色 + 气泡模式 → 点生成
→ 锁屏 / 切到别的 App，等通知
```

默认的懒人 ComfyUI 实例应该会出图；第一次提交可能要排队。

---

## 目录速览

```
android/
├── app/
│   ├── build.gradle.kts              ← 模块配置（minSdk / targetSdk / 依赖）
│   ├── proguard-rules.pro            ← R8 规则（debug 不走）
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/lifemanga/android/
│           ├── LifeMangaApplication.kt    ← Application 类
│           ├── MainActivity.kt           ← Compose 唯一 Activity
│           ├── ServiceLocator.kt         ← 手写 DI 容器
│           ├── data/                     ← 数据层
│           │   ├── AppSettingsStore.kt   ← DataStore + AppSettings 数据类
│           │   ├── AppDatabase.kt        ← Room
│           │   ├── MangaItemDao.kt       ←
│           │   ├── MangaItemEntity.kt    ←
│           │   ├── ProjectEntity.kt      ←
│           │   ├── CharacterEntity.kt    ←
│           │   ├── CharacterViewEntity.kt←
│           │   ├── CharacterRepository.kt←
│           │   ├── ProjectRepository.kt  ←
│           │   ├── Repository.kt         ← 生成历史聚合
│           │   ├── MangaStyle.kt         ← 8 种风格 enum + prompt
│           │   ├── BubbleMode.kt         ← 5 种气泡模式
│           │   ├── CharacterArtStyle.kt  ← 9 种角色风格
│           │   ├── EndpointType.kt       ← OpenAI / Azure
│           │   ├── EndpointConfig.kt     ←
│           │   ├── ImageStorage.kt       ←
│           │   └── SecureStore.kt        ← EncryptedSharedPreferences
│           ├── network/                  ← 网络层
│           │   ├── ComfyUIClient.kt      ← 上传 / 提交 / 轮询 / 下载
│           │   ├── ComfyWorkflows.kt     ← z_image workflow JSON 构造
│           │   ├── OpenAIClient.kt       ← 代码留存，未启用
│           │   ├── QwenClient.kt         ← vLLM /chat/completions
│           │   ├── MangaPrompts.kt       ← prompt 构建
│           │   ├── ImageCompression.kt   ←
│           │   ├── Dto.kt                ←
│           │   └── QwenDto.kt            ←
│           ├── ui/                       ← Compose 屏幕
│           │   ├── AppNav.kt             ← NavHost
│           │   ├── create/               ← 创作
│           │   ├── history/              ← 历史
│           │   ├── detail/               ← 详情
│           │   ├── settings/             ← 设置
│           │   ├── characters/           ← 角色库
│           │   ├── projects/             ← 工程列表
│           │   └── theme/                ←
│           └── work/                     ← WorkManager
│               ├── MangaGenerationWorker.kt   ← 主图生成
│               ├── CharacterGenerationWorker.kt← 角色视图
│               ├── StoryScriptWorker.kt       ← 故事剧本
│               └── NotificationHelper.kt      ← 通知 channel + 进度
├── build.gradle.kts                  ← 根 build（plugins 声明）
├── settings.gradle.kts               ← settings（仓库、模块）
├── gradle.properties                 ← AndroidX / Kotlin code style
├── gradle/
│   ├── libs.versions.toml            ← 版本目录
│   └── wrapper/                      ← Gradle wrapper
└── README.md                         ← 你正在看的
```

---

## 改动频率较高的位置

| 想改 | 看这里 |
|---|---|
| 加 / 改漫画风格 | `data/MangaStyle.kt` |
| 加 / 改气泡模式 | `data/BubbleMode.kt` |
| 改后端 URL 默认值 | `data/AppSettingsStore.kt`（`comfyUiUrl` / `qwenUrl` 默认值） |
| 改采样参数 | `network/ComfyWorkflows.kt`（KSampler 节点） |
| 改创作流 | `ui/create/CreateViewModel.startGeneration()` → `work/MangaGenerationWorker` |
| 改持久化字段 | `data/AppDatabase.kt` 升 version + 写 `Migration` |
| 改导航 | `ui/AppNav.kt` |

---

## Debug 技巧

**看后台 Worker 跑没跑**：
```bash
adb shell dumpsys jobscheduler | grep -A 20 lifemanga
```

**看通知**：
```bash
adb shell cmd notification list | grep lifemanga
```

**抓网络请求**：所有 ComfyUI / Qwen / OpenAI 调用都走 OkHttp，加个 `addInterceptor(HttpLoggingInterceptor().apply { level = BODY })` 就行（debug build 推荐），具体见 `network/*Client.kt`。

**模拟"ComfyUI 挂"**：把 `comfyUiUrl` 改成 `https://example.invalid`，提交任务看 Worker 失败 + 通知——比真的去关服务端快。
