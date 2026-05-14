# 架构说明

## 1. 工程与模块

| 模块 | 说明 |
|------|------|
| **`:starrysky`** | 核心 Android Library：播放、队列、MediaSession、通知、缓存、拦截链等 |
| **`:app`** | 示例宿主应用，演示初始化与 API 调用 |
| **`:extension-flac2120`**（默认未 `include`） | 历史 ExoPlayer 2.12 FLAC 扩展示例，一般无需启用 |

根 `settings.gradle` 当前包含 `:starrysky` 与 `:app`。

## 2. 分层结构（自顶向下）

```
业务层（宿主 Activity / ViewModel）
        │
        ▼
StarrySky（单例入口） ──► PlayerControl（门面 API）
        │                        │
        │                        ▼
        │                 PlaybackManager（队列、拦截链、状态协调）
        │                        │
        │                        ├─► MediaSessionManager（MediaSession / 系统媒体键）
        │                        │
        │                        └─► MusicPlaybackHost（运行时宿主抽象）
        │                                    │
        ├────────────────────────────────────┼──────────────────────────────┐
        ▼                                    ▼                              ▼
  StarrySkyInstall                    MusicService + Binder          MusicPlaybackFacade
  （全局配置、playbackHost）           （isConnectionService = true）   （isConnectionService = false，进程内）
                                                │
                                                ▼
                                         Playback 实现（默认 ExoPlayback，Media3 ExoPlayer）
```

要点：

- **`PlayerControl`**：对外暴露绝大多数播放与 UI 相关 API（播放列表、进度、音量、倍速等）。
- **`PlaybackManager`**：连接「队列 / 拦截器 / 引擎回调 / 通知状态 / MediaSession」；通过 **`MusicPlaybackHost`** 拿到真实 **`Playback`**（默认 **`ExoPlayback`**）。
- **`StarrySkyInstall`**：在 `apply()` 阶段根据配置选择 **bind `MusicService`** 或 **进程内 `MusicPlaybackFacade`**，并设置全局 **`playbackHost`**。

## 3. 双运行时策略（Service vs 进程内）

由 **`ServiceSettings.isConnectionService`** 控制（在 `StarrySkyInstall.init { service { ... } }` 中配置）：

| `isConnectionService` | 宿主实现 | 典型场景 |
|------------------------|-----------|----------|
| **`true`（默认）** | `bindService` → `MusicService` → `MusicServiceBinder`（实现 `MusicPlaybackHost`） | 需要后台播放、前台服务、与系统媒体控制深度结合 |
| **`false`** | `MusicRuntimeFactory.createInProcessHost` → **`MusicPlaybackFacade`** | Demo、单元测试、希望避免 bind 时序问题；**无 Service 时部分能力为 no-op**（如定时关播依赖 Service 的逻辑） |

`PlaybackManager` 会通过 **`playbackHost ?: StarrySkyInstall.playbackHost`** 解析当前宿主，避免「`StarrySky.with()` 早于 `onServiceConnected` 创建导致引擎一直为 null」类问题。

## 4. 播放数据流（简化）

1. 业务调用 `PlayerControl.playMusic*` / `playMusicByUrl` 等。
2. **`InterceptorPrepareChain`** 执行全局 + 局部 **`StarrySkyInterceptor`**（可做鉴权、换 URL、补封面等）。
3. 通过后调用 **`Playback.play`**（默认 **`ExoPlayback`**：构建 `MediaItem` / `MediaSource`，`ExoPlayer.prepare`）。
4. 引擎通过 **`Playback.Callback`** 回调 **`PlaybackManager`**，再经 **`PlaybackEventsSink`** 更新 **`LiveData` / `SharedFlow`** 与通知栏。

## 5. 与 Media3 的关系

- 默认实现 **`ExoPlayback`** 基于 **`androidx.media3`**（ExoPlayer、HLS/DASH/SS、RTMP DataSource、SimpleCache 等）。
- 宿主若需 **HLS / DASH / RTMP** 等，除依赖 `:starrysky` 外，通常还需在应用中声明与库一致的 **Media3 扩展模块**（参考 `:app` 的 `build.gradle`）。

## 6. 第二套播放链：`StarrySkyPlayer`

**`StarrySkyPlayer.create()`** 会创建 **独立的 `MusicPlaybackHost` + `PlayerControl`**，与 **`StarrySky.with()`** 的**全局单例**不共享队列与引擎。

使用原则：**播放、暂停、进度、seek、通知等必须在同一套 `player.with()` 上完成**，不要与 `StarrySky.with()` 混用。

详见 [API 使用说明](api-guide.md) 与 [注意事项](notes-and-faq.md)。
