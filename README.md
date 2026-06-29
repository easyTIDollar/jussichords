# jussichords

一款基于 Kotlin、Jetpack Compose 与 Material 3 构建的 Android 音乐客户端。项目聚焦轻量化播放体验、清爽的移动端界面以及可自定义的音乐服务接入能力，适合用于 Android 现代化架构、Compose UI、Media3 播放链路与网络音乐 API 调用的学习和二次开发。

## 项目特性

- 使用 Jetpack Compose 构建声明式 UI，整体遵循 Material 3 设计规范。
- 基于 Media3 实现音乐播放、后台播放、播放队列与媒体会话能力。
- 支持歌单、专辑、歌手、搜索、排行榜、每日推荐、云盘音乐等常用音乐场景。
- 支持账号登录、用户主页、关注列表、收藏与播放记录等用户相关能力。
- 内置歌词展示、睡眠定时、音质选择、播放模式切换等播放器功能。
- 支持自定义 API 服务地址，便于接入自建或私有化音乐服务。
- 采用 Hilt、Ktor、Coil、DataStore、Paging、Protocol Buffers 等现代 Android 技术栈。

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose、Material 3
- 架构：MVVM、Hilt 依赖注入
- 播放：AndroidX Media3
- 网络：Ktor Client
- 图片：Coil
- 数据：DataStore、Protocol Buffers
- 分页：AndroidX Paging
- 构建：Gradle Kotlin DSL

## 项目结构

```text
.
├── app/                 # Android 应用主体
├── ncmapi/              # 音乐 API 封装模块
├── gradle/              # Gradle Wrapper 与版本目录
├── screenshot/          # 项目截图
├── build.gradle.kts     # 根构建脚本
├── settings.gradle.kts  # Gradle 项目配置
└── README.md            # 项目说明文档
```

## 快速开始

### 环境要求

- Android Studio Ladybug 或更新版本
- JDK 17
- Android SDK 34
- Gradle Wrapper 使用仓库内置版本

### 克隆项目

```bash
git clone https://github.com/easyTIDollar/jussichords.git
cd jussichords
```

### 构建 Debug 版本

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

构建产物默认位于：

```text
app/build/outputs/apk/debug/
```

### Release 签名配置

如需构建 Release 包，可在项目根目录创建或更新 `local.properties`，并加入以下配置：

```properties
RELEASE_STORE_FILE=your-keystore.jks
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_KEY_PASSWORD=your-key-password
```

然后执行：

```bash
./gradlew assembleRelease
```

`local.properties` 已被 `.gitignore` 忽略，请不要将签名文件或敏感信息提交到仓库。


## 开发说明

- 默认包名：`com.jussicodes.music`
- 默认应用名：`jussichords`
- 主模块：`:app`
- API 模块：`:ncmapi`
- Debug 包会追加 `.debug` applicationId 后缀，便于与正式包共存。

## 致谢

本项目在界面、功能设计和技术实现上参考并使用了以下优秀开源项目与生态能力：

- [JetMelo](https://github.com/rcmiku/JetMelo)
- [InnerTune](https://github.com/z-huang/InnerTune)
- [Ktor](https://github.com/ktorio/ktor)
- [Coil](https://github.com/coil-kt/coil)
- [AndroidX Media3](https://developer.android.com/media/media3)
- [Protocol Buffers](https://github.com/protocolbuffers/protobuf)
- [Reorderable](https://github.com/Calvin-LL/Reorderable)

## 免责声明

本项目仅用于学习、研究与技术交流，不得用于商业用途或任何违反当地法律法规的场景。

本项目与网易云音乐、杭州网易云音乐科技有限公司及其任何关联公司不存在隶属、授权、赞助或认可关系。项目中涉及的商标、服务标识、产品名称及其他知识产权均归其各自权利人所有。

本项目不提供任何 VIP 音频解密、版权规避或非法解锁服务。访问相关内容前，请确保你已在对应平台取得合法授权或会员资格。

## License

本项目基于 [GPL-3.0 License](LICENSE) 开源。
