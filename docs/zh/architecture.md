# 架构说明

## 1. 模块

| 模块 | 说明 |
|------|------|
| **`:starrysky`** | 核心 Android Library：播放、队列、MediaSession、通知、缓存、拦截链、音效、SoundPool |
| **`:app`** | 示例应用，演示初始化与 API |

## 2. 调用链（简图）

```
宿主 UI / ViewModel
        │
        ▼
StarrySky.with() ──► PlayerControl（对外 API）
        │
        ▼
PlaybackManager（队列、拦截链、状态、MediaSession）
        │
        ├─► MusicPlaybackHost
        │         │
        │         ├─ isConnectionService = true  → MusicService / Binder
        │         └─ isConnectionService = false → MusicPlaybackFacade（进程内）
        │
        └─► Playback（默认 ExoPlayback，Media3 ExoPlayer）
```

## 3. 双运行时

- **`isConnectionService = true`**：异步 `bindService`，适合后台播放与系统媒体控制。  
- **`isConnectionService = false`**：进程内门面，无 bind 时序问题；依赖 Service 的定时关播等能力不生效。

`PlaybackManager` 通过 **`playbackHost ?: StarrySkyInstall.playbackHost`** 解析当前宿主，保证在 `StarrySky.with()` 早于 `onServiceConnected` 创建时仍能拿到引擎。

## 4. `StarrySkyPlayer`

**独立第二套** `PlayerControl` + 宿主，与 **`StarrySky.with()`** 不共享队列与引擎；所有操作须在 **同一** `player.with()` 上完成。

详见 [集成与各功能说明](installation-and-configuration.md) 与 [API 参考](api-guide.md)。
