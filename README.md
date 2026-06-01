# 漫画人生 · LifeManga

> 把生活中随手拍的照片，自动转化成日式漫画风格的 App。
> 项目早期是 iOS 版，已完全迁到 Android；图片生成后端从 OpenAI `gpt-image-2` 切到**本地 ComfyUI + z_image workflow**——不再需要任何云端 API Key，开箱即用。

**技术栈**：Kotlin 2.0 · Jetpack Compose · Android (minSdk 26 / targetSdk 34) · ComfyUI + z_image · Qwen3.5-9B (vLLM)

---

## 现状速览

| 模块 | 状态 |
|---|---|
| 多参考图（最多 6 张）→ 漫画生成 | ✅ 走 ComfyUI + z_image |
| 8 种漫画风格 + 彩色/黑白切换 | ✅ |
| 5 种气泡文字模式（不画 / 空白 / 中文 / 日文 / 英文） | ✅ |
| 自由补充 prompt | ✅ |
| 后台不中断（WorkManager + 前台服务 + 通知） | ✅ |
| 实时生成日志（首字节 / 步数 / 错误码） | ✅ |
| 60 秒同请求 hash 去重 | ✅ |
| 生成历史（Room 持久化） | ✅ |
| 工程（Projects）隔离每部漫画 | ✅ 列表 + 切换 |
| 「续接前一张」 | ✅ 从历史挑一张作为新图的参考 |
| 角色库（9 种艺术风格 + 多视图设定稿） | ✅ 可独立生成 |
| 角色载入创作页 | ✅ 角色详情页一键塞进参考图位 |
| 故事模式（Qwen 写剧本，4/6/8 格） | ✅ 剧本生成；暂未串到创作页直接编辑 |
| OpenAI / Azure 后端 | ✅ 已彻底移除（代码 + DataStore 字段 + Settings 卡片全清） |
| 失败任务输入图快照恢复 | ❌ Android 端未实现 |

---

## 截图

仓库里 `doc/` 下的 4 张样例图来自**早期 iOS 版**，跟现在 Android 端 UI 长得不一样。Android 端没截过图，凑合看一下风格示例：

| 萌系四格 | 经典少年 Jump · 大阪 | 经典少年 Jump · 雪夜 |
|:---:|:---:|:---:|
| <img src="doc/output-chibi.jpg" width="240"> | <img src="doc/output-jump-osaka.jpg" width="240"> | <img src="doc/output-jump-snow.jpg" width="240"> |

> 这几张都是 App 里直接拍照 / 选图生成的，没经过后期。**风格 prompt 在 `android/app/src/main/java/com/lifemanga/android/data/MangaStyle.kt` 里**，可改。

---

## 跑起来 · 三步

### ① 拿 APK

两种方式，二选一：

**A. 下一份别人打好的**（最快）
1. 打开 https://github.com/shaozheng0503/LifeManga/actions
2. 点最近一次 `Build Android APK` 运行
3. 滚到底 **Artifacts** → 下载 `LifeManga-debug-apk.zip`
4. 解压得到 `LifeManga-debug-<hash>.apk`，发到手机装上
5. 首次安装需要在「设置 → 应用 → 特殊权限 → 安装未知应用」允许来源

**B. 自己本地打**（需要 Android Studio Hedgehog+ 或 JDK 17 + Gradle 8.10+）
```bash
cd LifeManga/android
./gradlew assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

> GitHub Actions 在每次 `push` 到 `main` 的 `android/**` 路径时**自动**打 debug APK，保留 30 天。

### ② 后端：起一个 ComfyUI

App 默认连的是「懒人托管实例」（`https://deployment-452-2eu5fpbp-8188.550w.link`），开箱就能用，**不需要任何 API Key**。但实例是公共的，会排队。

要私有部署，自己起一个：

1. **装 ComfyUI**（GPU 机器，VRAM ≥ 12GB 推荐）
   ```bash
   git clone https://github.com/comfyanonymous/ComfyUI.git
   cd ComfyUI
   pip install -r requirements.txt
   ```
2. **下模型**到对应目录：
   ```
   ComfyUI/models/
   ├── unet/z_image_bf16.safetensors
   ├── clip/qwen_3_4b_fp8_mixed.safetensors
   └── vae/ae.safetensors
   ```
3. **启动**：`python main.py --listen 0.0.0.0 --port 8188`
4. App 设置页 → **ComfyUI URL** 改成 `http://<你的 IP>:8188`

### ③ 故事模式（可选）

故事模式调的是 Qwen3.5-9B vLLM，默认同样指向懒人实例。私有部署需要自己跑：

```bash
python -m vllm.entrypoints.openai.api_server \
  --model Qwen/Qwen3.5-9B \
  --port 8000
```

然后在 App 设置里改 **Qwen URL**。

> 只想玩图片生成可以完全跳过 Qwen 那一段，不影响主流程。

---

## 关键技术点

| 关注点 | 实现 |
|---|---|
| 后端 | ComfyUI `/prompt` 提交 workflow JSON → 轮询 `/history/{promptId}` → `/view` 拉图 |
| Workflow 模板 | `network/ComfyWorkflows.kt`：`textToManga` / `imageToManga` / `multiImageToManga` 三种 |
| 采样参数 | KSampler res_multistep / simple / 25 steps / cfg 4.0，shift 3.0 |
| 鉴权 | **本地 z_image workflow 零鉴权**；`extra_data.api_key_comfy_org` 字段只在用 comfy.org 云端节点时才需要 |
| 后台不中断 | `WorkManager` + `CoroutineWorker.setForeground()` + 通知 channel，锁屏 / 切 App 不杀 |
| 多图上传 | HTTP multipart 上传到 `/upload/image`，文件名回填到 workflow 的 `LoadImage` 节点 |
| 上传体积 | `ImageCompression.kt` 强制 JPEG，迭代压到 ≤ 500KB |
| 持久化 | Room（生成历史 / 工程 / 角色）+ DataStore（设置）+ EncryptedSharedPreferences（comfy.org Key） |
| API Key 安全 | AES-256-GCM 加密，**只在本机**，不联网 |
| 实时日志 | 内存中按 jobId 累积，含 INFO / SUCCESS / WARNING / ERROR 五级 |
| 同请求去重 | `CreateViewModel` 算 SHA256(sortedPaths + style + isColor + bubble + prompt)，60s 内同 hash 拦截并提示 |
| 跨屏参考图意图 | `data/ReferenceIntent.kt` 单例，角色详情/历史 picker 写、CreateViewModel.init 消费 |
| 续接前一张 | `Routes.PICK_REFERENCE` + `savedStateHandle` 回传单张路径 |
| 任务状态机 | `running → done / failed`，失败任务可手动"重新生成" |

---

## 项目结构

```
LifeManga/
├── README.md                          ← 你正在看的
├── LICENSE
├── .github/workflows/build-apk.yml    ← APK 自动构建
├── android/                           ← Android Studio 项目根
│   ├── README.md                      ← Android 端独立说明
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/lifemanga/android/
│   │           ├── LifeMangaApplication.kt
│   │           ├── MainActivity.kt
│   │           ├── ServiceLocator.kt
│   │           ├── data/              ← Room / DataStore / Enum / 仓库
│   │           ├── network/           ← ComfyUI / Qwen 客户端 + Workflow
│   │           ├── ui/                ← Compose 屏幕
│   │           │   ├── create/        ← 创作页
│   │           │   ├── history/       ← 历史页
│   │           │   ├── detail/        ← 详情页
│   │           │   ├── settings/      ← 设置页
│   │           │   ├── characters/    ← 角色库
│   │           │   ├── projects/      ← 工程列表
│   │           │   └── theme/
│   │           └── work/              ← WorkManager Workers + 通知
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/libs.versions.toml      ← 依赖版本目录
└── doc/                               ← 样例图（来自早期 iOS 版）
```

---

## 修改 / 扩展

### 改漫画风格 prompt
打开 `android/app/src/main/java/com/lifemanga/android/data/MangaStyle.kt`，8 个 enum case 里的 `rawPrompt` 就是喂给 ComfyUI 的英文风格描述。`effectivePrompt(isColor)` 会自动叠上"彩色 / 黑白"前缀和"清洁墨线"全局规则。

### 改采样参数
`network/ComfyWorkflows.kt` 里 KSampler 节点的 `steps` / `cfg` / `sampler_name` / `denoise`。i2i 默认 `denoise = 0.75`，改这里会同时影响 `imageToManga` 和 `multiImageToManga`。

### 加新的生成后端
现在创作流写死在 `MangaGenerationWorker` → `ComfyUIClient`。要切到别的后端，**先**改这两处，再考虑加一个新的 `*Client`（参考 `QwenClient` 的写法）。

### 加新屏幕
Compose 路由在 `ui/AppNav.kt`，按 `create/` `history/` `detail/` `settings/` `characters/` `projects/` 的结构加 `*Screen.kt` + `*ViewModel.kt` 即可。

---

## 常见问题

**App 一进去就报"无法连接到 ComfyUI"？**
设置页 → ComfyUI URL 检查一下。默认懒人实例挂了的话，找一个能跑的 URL 自己换上。**不要去填 API Key**——本地 workflow 不需要。

**点了一下生成按钮没反应？**
60 秒去重拦了。算的是 (参考图集合 + 风格 + 彩色/黑白 + 气泡模式 + prompt) 的 SHA256，完全一样的请求 60 秒内直接 toast 提示「跟 X 秒前那次一样的请求，已拦截」。**改任意一项（哪怕加一张参考图或动一个字）就能重发**。

**点了"续接前一张"选完图怎么没看到？**
返回创作页时图会出现在参考图横滑里。如果没有，确认一下左下角进度条没卡在别的状态，或者重启一次 App（极端情况 savedStateHandle 漏了消费）。

**点"载入到创作"之后去哪了？**
角色详情页 → "载入到创作" 会把该角色所有视图塞进中转站 `ReferenceIntent`，然后跳回工程列表。**你选好工程再进创作页**，视图就自动出现在参考图位了。中间多走一步是因为角色页不在工程上下文里，直接跳转拿不到 projectId。

**生成出来黑屏 / 报错 `VAE not found` / `UNET not found`？**
ComfyUI 服务端的 `models/unet/` `models/clip/` `models/vae/` 三个目录里模型文件没下完整。见上面 ② 步。

**生成报 comfy.org 401？**
`ComfyUIClient` 在用 comfy.org 的云端节点（不是本地 z_image 节点）时会需要 API Key。本地 workflow 不应该报这个。检查一下你引用的 workflow JSON 里有没有 `"class_type": "ComfyOrgNode"` 之类的云端节点。

**iOS 版去哪了？**
仓库最早 commit (`c62fa3a`) 有 Swift 源码，已经 N 个 commit 没动过。如果想捡回来用 `git checkout c62fa3a -- LifeManga/` 翻一下历史，不保证能编过。

---

## 容易踩的坑

| 坑 | 现象 | 解决 |
|---|---|---|
| Android 13+ 默认不允许 HTTP | 私有 ComfyUI 配 `http://192.168.x.x:8188` 连不上 | 设置页默认实例用 HTTPS；自部署要么套 TLS，要么改 `network_security_config.xml` 放行局域网 |
| minSdk 26 | Android 7 以下装不上 | 真要支持老机器，改 `android/app/build.gradle.kts` 的 `minSdk` 并测一遍 |
| ComfyUI 在 CPU 上跑 | 出图极慢（10+ 分钟） | z_image 必须 GPU，CPU 不实用 |
| `denoise` 太高 (>0.85) | 图生图变"重画"而不是"续接" | 改 `ComfyWorkflows.imageToManga()` 的默认 `denoise` 参数 |
| 多张参考图只取第一张 | prompt 写了 6 张但风格没融合 | `multiImageToManga` 当前只用第一张做 i2i，prompt 里加 `multi-reference character consistency` 提示词 |

---

## 贡献

欢迎 Issue / PR。**新功能建议先开 Issue**——尤其是涉及后端切换的，最近这块改动频繁，方向没对齐就开 PR 是浪费功夫。

---

## License

[MIT](./LICENSE) — 随便用、随便改、随便商用。

ComfyUI / Qwen 调用的费用由你**自己的服务器 / 账号**承担，本项目作者不负责任何账单。ComfyUI + z_image 模型权重请遵守各自的许可证（z_image / Qwen / VAE 的 LICENSE 在各自仓库里）。
