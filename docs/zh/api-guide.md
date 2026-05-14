# API 参考与代码片段

以下默认使用 **`StarrySky.with()`** 返回的 **`PlayerControl`**。

---

## 1. `StarrySky` 静态方法

| API | 说明 |
|-----|------|
| `with()` | 获取全局 **`PlayerControl`** |
| `soundPool()` | **`SoundPoolPlayback`**（若运行时提供） |
| `changeNotification` / `openNotification` / `closeNotification` / `setIsOpenNotification` | 通知类型与显隐 |
| `getNotificationType` | 当前通知类型 |
| `isOpenCache` | 缓存开关是否打开 |
| `getPlayerCache` | 当前 **`ICache`** 实例 |
| `effect` / `effectSwitch` / `getEffectSwitch` / `saveEffectConfig` | 音效相关 |
| `interceptors` / `clearInterceptor` | 全局拦截器列表 |
| `release()` | 释放全局控制器与 Install 资源 |

`StarrySkyInstall.bindService` / `unBindService` 用于手动控制绑定，一般已由 **`apply()`** 处理。

---

## 2. `SongInfo`

| 字段 | 说明 |
|------|------|
| `songId` | 唯一 ID，列表内不可重复 |
| `songUrl` | 可播放地址（本地 path 或 http(s)） |
| `songName` / `artist` / `songCover` / `duration` | 可选，用于通知与 UI |

```kotlin
val info = SongInfo("track_01", "https://cdn.example.com/a.m4a", "标题")
info.artist = "歌手"
info.songCover = "https://cdn.example.com/cover.jpg"

SongInfo.create("https://cdn.example.com/b.mp3")
```

---

## 3. 播放与控制

```kotlin
val list = mutableListOf(info1, info2)
StarrySky.with().playMusic(list, 0)

StarrySky.with().playMusicByUrl("https://example.com/x.mp3")
StarrySky.with().playMusicById("track_01")
StarrySky.with().playMusicByInfo(info1)

StarrySky.with().pauseMusic()
StarrySky.with().restoreMusic()
StarrySky.with().stopMusic()
StarrySky.with().replayCurrMusic()

StarrySky.with().skipToNext()
StarrySky.with().skipToPrevious()

StarrySky.with().seekTo(30_000L, isPlayWhenPaused = true)
```

---

## 4. 列表与模式

```kotlin
val all = StarrySky.with().getPlayList()
StarrySky.with().updatePlayList(newList)
StarrySky.with().addSongInfo(SongInfo("3", url, "三"))
StarrySky.with().removeSongInfo("2")
StarrySky.with().clearPlayList()

StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_SHUFFLE, isLoop = false)
val mode = StarrySky.with().getRepeatMode()
```

---

## 5. 状态与进度

```kotlin
val playing = StarrySky.with().isPlaying()
val pos = StarrySky.with().getPlayingPosition()
val dur = StarrySky.with().getDuration()
val buf = StarrySky.with().getBufferedPosition()

StarrySky.with().setVolume(0.8f)
StarrySky.with().onDerailleur(refer = false, multiple = 1.25f)
```

**进度监听（务必带 tag，并在 `onPause` 移除）：**

```kotlin
StarrySky.with().setOnPlayProgressListener(listener, tag = this.toString())
StarrySky.with().removeProgressListener(this.toString())
```

**`LiveData` / `Flow`：**

```kotlin
StarrySky.with().playbackState().observe(owner) { }
StarrySky.with().playbackStageFlow() // SharedFlow
```

---

## 6. 链式局部选项（单次播放）

```kotlin
StarrySky.with()
    .skipMediaQueue(true)
    .setWithOutCallback(false)
    .addInterceptor(myInterceptor)
    .playMusicByUrl("https://example.com/once.mp3")
```

`skipMediaQueue(true)` 与部分「列表类」API 互斥，违例会抛 **`IllegalStateException`**，以源码为准。

---

## 7. `StarrySkyPlayer`

```kotlin
val p = StarrySkyPlayer.create(userGlobalConfig = false)
p.setOpenCache(true)
p.setCacheDestFileDir(cacheDir)
p.with().playMusicByUrl(url)
p.with().pauseMusic()
```

---

## 8. 拦截器签名

```kotlin
abstract class StarrySkyInterceptor {
    abstract fun getTag(): String
    open fun process(songInfo: SongInfo?, callback: InterceptCallback) {}
}
```

- 继续：`callback.onNext(songInfo)`  
- 中断：`callback.onInterrupt("原因")` → 进入错误状态  

更多示例见 **[集成与各功能说明](installation-and-configuration.md)** 中的「拦截器」一节。
