# API 使用说明

## 1. 入口与单例

### 1.1 `StarrySky.with(): PlayerControl`

全局 **单例** `PlayerControl`，绝大多数播放能力通过它调用：

```kotlin
StarrySky.with().playMusicByUrl("https://example.com/audio.mp3")
StarrySky.with().pauseMusic()
```

首次调用时会创建 `PlayerControl`；内部会通过 **`StarrySkyInstall.playbackHost`** 解析当前 **`MusicPlaybackHost`** 与 **`Playback`** 引擎，与异步 `bindService` 完成顺序解耦（见 [架构说明](architecture.md)）。

### 1.2 `StarrySky` 上的其它静态方法

| API | 作用 |
|-----|------|
| `soundPool()` | 获取 `SoundPoolPlayback`（若运行时支持） |
| `changeNotification` / `openNotification` / `closeNotification` / `setIsOpenNotification` | 通知栏类型与显隐 |
| `getNotificationType` | 当前通知类型 |
| `isOpenCache` / `getPlayerCache` | 缓存开关与缓存实现引用 |
| `effect` / `effectSwitch` / `getEffectSwitch` / `saveEffectConfig` | 音效相关 |
| `interceptors` / `clearInterceptor` | 全局拦截器列表维护 |
| `release()` | 释放全局 `PlayerControl` 与 Install 资源 |

`bindService` / `unBindService` 在 **`StarrySkyInstall`** 上，一般已由 `apply()` 处理；特殊场景可查阅源码。

---

## 2. `SongInfo`

播放载体。**`songId` 与 `songUrl` 在播放时不可为空**；同一播放列表内 **`songId` 应唯一**。

常用构造：

```kotlin
SongInfo(songId = "id1", songUrl = "https://...", songName = "标题")
SongInfo.create("https://...")   // songId 默认由 url 派生（如 md5）
```

可选字段：封面、艺术家、时长等；拦截器里可补全。

---

## 3. `PlayerControl`：播放与队列

### 3.1 发起播放

| API | 说明 |
|-----|------|
| `playMusic(list, index)` | 使用列表与下标播放 |
| `playMusicById(songId)` | 按 id 播放（需列表中已有该 id） |
| `playMusicByUrl(url)` | 按 URL 播放（会加入队列） |
| `playMusicByInfo(info)` | 按 `SongInfo` 播放 |

### 3.2 控制与信息

| API | 说明 |
|-----|------|
| `pauseMusic` / `restoreMusic` / `stopMusic` | 暂停、恢复、停止 |
| `prepare` / `prepareById` / `prepareByUrl` / `prepareByInfo` | 仅准备不立即 play（部分模式受限） |
| `skipToNext` / `skipToPrevious` | 上一曲 / 下一曲（`skipMediaQueue` 模式下受限） |
| `seekTo(pos, isPlayWhenPaused)` | 跳转进度 |
| `replayCurrMusic` | 重播当前曲 |
| `fastForward` / `rewind` / `onDerailleur` | 倍速 / 变速 |

### 3.3 列表与模式

| API | 说明 |
|-----|------|
| `getPlayList` / `updatePlayList` / `addPlayList` | 列表读写 |
| `addSongInfo` / `removeSongInfo` / `clearPlayList` | 增删清空 |
| `setRepeatMode` / `getRepeatMode` | 顺序 / 单曲 / 随机 / 倒序及循环 |
| `updateShuffleSongList` / `updateCurrIndex` | 随机队列刷新、索引校正 |

### 3.4 状态查询

| API | 说明 |
|-----|------|
| `isPlaying` / `isPaused` / `isIdle` / `isBuffering` | 优先与底层 **`Playback.playbackState()`** 对齐，引擎不可用时回退 `LiveData` |
| `getNowPlayingSongInfo` / `getNowPlayingSongId` / `getNowPlayingSongUrl` | 当前曲目 |
| `getNowPlayingIndex` / `getPlaybackQueueIndex` | 队列下标 |
| `getPlayingPosition` / `getBufferedPosition` / `getDuration` | 进度与缓冲 |
| `isSkipToNextEnabled` / `isSkipToPreviousEnabled` | 是否可切歌 |
| `getVolume` / `setVolume` | 音量 0～1（或 0～100 自动换算） |
| `getPlaybackSpeed` | 当前倍速 |
| `getAudioSessionId` | 音效等使用 |
| `querySongInfoInLocal(context)` | 扫描本地音频（需存储权限等由宿主处理） |

### 3.5 定时关播

```kotlin
StarrySky.with().stopByTimedOff(timeMs, isPause = true, isFinishCurrSong = false)
```

**依赖 `MusicService` 的实现**；`isConnectionService = false` 时计时逻辑为 **no-op**（见 [注意事项](notes-and-faq.md)）。

### 3.6 监听与 Flow / LiveData

| API | 说明 |
|-----|------|
| `playbackState(): LiveData<PlaybackStage>` | 播放阶段：`IDLE` / `BUFFERING` / `PLAYING` / `PAUSE` / `SWITCH` / `ERROR` 等 |
| `playbackStageFlow()` / `focusChangeFlow()` | Kotlin **SharedFlow** 形式 |
| `focusStateChange()` | 焦点 **LiveData** |
| `setOnPlayProgressListener(listener, tag)` | 周期进度回调；**务必传稳定 tag**，建议在 `onResume` 注册、`onPause` `removeProgressListener` |
| `addPlayerEventListener` / `removePlayerEventListener` / `clearPlayerEventListener` | 播放阶段扩展监听 |

### 3.7 链式局部配置（Activity 级）

| API | 说明 |
|-----|------|
| `skipMediaQueue(Boolean)` | 是否跳过队列直连引擎；**与列表类 API 互斥规则见源码抛错说明** |
| `setWithOutCallback(Boolean)` | 是否暂时关闭状态回调 |
| `addInterceptor` | 本次播放链上的局部拦截器，执行完会清理 |

---

## 4. `StarrySkyPlayer`（第二套播放链）

适用于需要 **独立通知 / 独立缓存目录 / 独立焦点策略** 的场景：

```kotlin
val player = StarrySkyPlayer.create()
    .setAutoManagerFocus(false)
    .setOpenCache(true)
// ...
player.with().playMusicByUrl(url)
player.with().setOnPlayProgressListener(listener, tag = "tag_b")
```

**禁止**与 **`StarrySky.with()`** 混用同一套 UI 控制（否则会出现「能播但进度条/暂停无效」）。

---

## 5. 拦截器 `StarrySkyInterceptor`

实现 `process(songInfo, callback)`：

- 继续：`callback.onNext(songInfo)`（可替换修改后的 `SongInfo`）
- 中断：`callback.onInterrupt("原因")` → 播放失败 / `ERROR` 状态

可配合 **`InterceptorThread.IO`** 在非主线程执行耗时逻辑。

---

## 6. 与旧文档表格的对照

仓库内 [docs/播放相关api介绍.md](../播放相关api介绍.md) 中有「编号对照表」，可与本节互补；若与源码不一致，**以源码 `PlayerControl` / `StarrySky` 为准**。
