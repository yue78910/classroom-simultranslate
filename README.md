# 课堂同声传译（Android 平板）

面向中外合作办学课堂的实时同声传译应用：平板麦克风收音，应用内全屏显示双语对照字幕（原文 + 译文），默认英 → 中，也可手动切到中 → 英。

## 已实现

- 原生 Android：Kotlin + Jetpack Compose，minSdk 29（Android 10+），支持横竖屏大字幕。
- 在线模式：OpenAI Realtime 翻译会话（`gpt-realtime-translate` + `gpt-realtime-whisper` 输入转写），24 kHz PCM16 流式上传，实时输出双语字幕。
- 离线模式：sherpa-onnx 中英流式语音识别 + NLLB-200-distilled-600M INT8 ONNX 翻译，模型在应用内下载后完全离线可用。
- 自动回退：默认 Auto 模式，在线连接失败时自动切换到离线引擎，并提示原因。
- 模型管理：应用内下载页支持断点续传、哈希校验（manifest 提供时）、删除与安装状态。
- 设置：OpenAI API Key 加密本地保存、翻译方向、引擎模式、字幕字号。

## 构建 APK

需要 JDK 17、Android SDK（platform 35、build-tools 35.0.0）和 Gradle 8.10.2。

```powershell
# 设置 JDK（示例）
$env:JAVA_HOME = "D:\ChatGPT\同声传译\.tools\jdk\jdk-17.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 首次构建
.\gradlew.bat :app:assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。Release 包默认使用 debug 密钥签名，方便直接侧载：

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK 位于 `app/build/outputs/apk/release/app-release.apk`。正式分发时建议替换为自己的 keystore。

如果本机没有 Android 工具链，项目里的 `.tools` 目录（已 gitignore）可自行放置：

```text
.tools\jdk\jdk-17.0.2\
.tools\android-sdk\
local.properties  ->  sdk.dir=D\:\\ChatGPT\\同声传译\\.tools\\android-sdk
```

## 安装到平板

1. 把 `app-release.apk` 或 `app-debug.apk` 通过 USB、网盘或浏览器传到 Android 平板。
2. 在平板文件管理器中点击 APK，允许“安装未知来源应用”。
3. 首次使用授予麦克风权限。
4. 在“设置”里粘贴 OpenAI API Key（保存在平板本地加密存储中）。
5. 需要离线能力时，先到“离线模型”页下载两个模型包（共约 1.1-1.5GB），建议使用 Wi-Fi。

## 使用建议

- 课堂场景默认选“英 → 中”，外教讲课即实时出现中文译文。
- 平板尽量靠近讲话人，或外接领夹麦/蓝牙麦，可明显提高识别准确率。
- 通信工程术语由在线模型直接处理；`gpt-realtime-translate` 目前不支持自定义领域词汇表，若遇到个别术语不理想，可在后续版本接入自定义词汇映射。
- 在线模式按音频时长计费，长课时注意 API 用量。

## 离线模型

manifest 在 `app/src/main/assets/offline_models.json`：

- ASR：`sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`（GitHub Release tar.bz2）
- MT：NLLB-200-distilled-600M INT8（encoder 约 415MB + decoder 约 729MB，Hugging Face）

两个文件源的 SHA-256 留空，下载器会在 manifest 提供哈希时强制校验；发布前建议补全哈希。`sentencepiece.bpe.model` 取自上表的 `facebook/nllb-200-distilled-600M`。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

覆盖：字幕增量合并、16k→24k 重采样、中英文检测、模型 manifest 解析、SentencePiece 模型解析、在线 WebSocket 协议（MockWebServer）。

如果项目位于 Windows 中文路径下，Gradle 单测 worker 可能因 classpath 编码问题报
`ClassNotFoundException`，可把构建目录指到 ASCII 路径再跑：

```powershell
.\gradlew.bat :app:testDebugUnitTest -PbuildDirOverride=C:/temp/simultranslate-build
```

真机验收清单见 [docs/acceptance.md](docs/acceptance.md)。

## 已知边界

- 在线翻译会话最长约 60 分钟，长课建议分段重启会话（v1 未做自动重连续接）。
- 离线 NLLB 无 KV cache 贪心解码，长句翻译耗时会上升；v1 接受“句子结束后约 3-5 秒出译文”。
- 离线 NLLB 推理按标准 ONNX 输入名（`input_ids`、`encoder_hidden_states` 等）实现，若换用其他导出版本需同步调整输入映射。
- 未做语音播报、悬浮窗、抓取其他 App 声音、音频文件导入。
