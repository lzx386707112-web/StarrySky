# StarrySky 文档（中文）

本目录为 **StarrySky** 播放库的分篇说明，与源码模块 `:starrysky` 对应（Kotlin + AndroidX + **Media3 / ExoPlayer**）。

## 文档索引

| 文档 | 内容 |
|------|------|
| [架构说明](architecture.md) | 模块划分、运行时分层、数据流、与 `MusicService` 的关系 |
| [集成与初始化](installation-and-configuration.md) | Gradle 依赖、Manifest 合并、Application 初始化与各配置项 |
| [API 使用说明](api-guide.md) | `StarrySky` / `PlayerControl` / `StarrySkyPlayer`、播放列表、监听与拦截器 |
| [注意事项与排错](notes-and-faq.md) | 明文 HTTP、权限、双实例、进度回调、与其他 Exo 依赖共存等 |

## 快速入口

- **全局播放控制**：`StarrySky.with()` → `PlayerControl`
- **初始化**：`StarrySkyInstall.init(application) { ... }.apply()`
- **示例应用**：模块 `:app` 中的 `TestApplication`、`TestActivity`

## 版本与依赖（当前仓库）

- **库模块**：`:starrysky`（`compileSdk` / `targetSdk` 34，`minSdk` 21）
- **Media3**：`androidx.media3` **1.3.0**（见根 `build.gradle` 中 `media3_version`）

## 外部历史文档

若需对照旧版说明，可参考仓库内 [docs/README.md](../README.md) 及原作者 [在线文档](https://espoirx.github.io/StarrySky/#/)（部分内容可能落后于当前 Media3 分支）。
