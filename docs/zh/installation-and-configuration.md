# 集成与各功能说明（含示例）

本文与当前源码分支一致：依赖 **JitPack**、初始化 **`StarrySkyInstall.init { }.apply()`**，并逐项说明 **缓存、通知栏、图片加载、拦截器、后台 Service、音频焦点、音效、SoundPool、全局监听、播放模式、定时关播** 等。

---

## 1. 依赖（JitPack）

**`settings.gradle`：**

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**模块 `build.gradle`：**（`Tag` 改为实际版本，如 `2.7.0`）

```groovy
dependencies {
    implementation 'com.github.lzx386707112-web:StarrySky:Tag'
}
```

### 流式媒体扩展（宿主按需添加）

库默认带核心 Media3 ExoPlayer；若需 **HLS / DASH / SS / RTMP** 等，请在宿主增加与库 **相同版本号** 的依赖（版本以你工程或库发布说明为准，示例）：

```groovy
def m3 = "1.3.0" // 与 StarrySky 依赖的 media3 版本对齐
implementation "androidx.media3:media3-exoplayer-hls:$m3"
implementation "androidx.media3:media3-exoplayer-dash:$m3"
implementation "androidx.media3:media3-exoplayer-smoothstreaming:$m3"
implementation "androidx.media3:media3-datasource-rtmp:$m3"
```

---

## 2. Manifest 与权限

库 `AndroidManifest` 会 **合并** 进宿主，包含 `MusicService`、前台服务类型、网络、Wi‑Fi、唤醒锁等声明。

宿主仍需注意：

- **通知**：Android 13+ 自行在合适时机申请 **`POST_NOTIFICATIONS`**（库仅合并声明）。  
- **蓝牙**：若使用蓝牙耳机断开暂停等逻辑，按需申请 **`BLUETOOTH_CONNECT`** 等。  
- **明文 HTTP**：`targetSdk` 较高时默认禁止 cleartext，需 **`usesCleartextTraffic`** 或 **`networkSecurityConfig`**。  
- **存储**：缓存目录建议使用 **`getExternalFilesDir`**，避免宽泛的读写外置存储权限。

---

## 3. 初始化总览

在 **`Application.onCreate`**（**主进程**）中：

```kotlin
StarrySkyInstall.init(this) {
    isDebug = BuildConfig.DEBUG
    isAutoManagerFocus = true
    playback = null
    globalPlaybackStageListener = null

    service { /* 见 §4 */ }
    cache { /* 见 §5 */ }
    notification { /* 见 §6 */ }
    image { /* 见 §7 */ }

    addInterceptor(/* 见 §8，可选 */)
}.apply()
```

**必须调用 `.apply()`**，否则不会注册生命周期、创建 `playbackHost`、绑定 Service（若开启）等。

---

## 4. 后台服务（`service { }`）

| 配置项 | 含义 |
|--------|------|
| **`isConnectionService`** | `true`：通过 `bindService` 使用 **`MusicService`**，适合真后台播放；`false`：进程内 **`MusicPlaybackFacade`**，调试简单，**无 Service 时「定时关播-播完再停」等逻辑不生效**。 |
| **`startForegroundByWorkManager`** | 部分机型后台启动前台受限时，是否通过 **WorkManager** 兜底展示前台通知。 |
| **`isStartService` / `onlyStartService`** | 是否在 bind 前先 `startService` / 前台服务策略。 |
| **`connection`** | 可选 **`ServiceConnection`**，监听 bind 成功/断开。 |

**示例 A：正式环境（走 Service）**

```kotlin
service {
    isConnectionService = true
    startForegroundByWorkManager = true
    connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { }
        override fun onServiceDisconnected(name: ComponentName?) { }
    }
}
```

**示例 B：仅调试（进程内）**

```kotlin
service {
    isConnectionService = false
}
```

---

## 5. 边播边缓存（`cache { }` + 运行时开关）

| 配置项 | 含义 |
|--------|------|
| **`isOpen`** | 初始化时是否打开缓存（与运行时 `cacheSwitch` 配合）。 |
| **`destFileDir`** | 缓存根目录（建议 **`context.getExternalFilesDir(null)` 子目录**）。 |
| **`maxBytes`** | 最大缓存体积。 |
| **`impl`** | 自定义 **`ICache`**；默认使用库内基于 Media3 **`SimpleCache`** 的实现。 |

**初始化示例：**

```kotlin
cache {
    isOpen = true
    destFileDir = applicationContext.getExternalFilesDir(null)!!.absolutePath + "/starrysky_cache"
    maxBytes = 1024L * 1024 * 1024
}
```

**运行时切换（仅影响「是否走缓存逻辑」开关常量；若需立刻重建管道，可在切换后 stop 再重新 play）：**

```kotlin
StarrySky.with().cacheSwitch(true)
```

---

## 6. 通知栏（`notification { }` + `NotificationConfig`）

| 配置项 | 含义 |
|--------|------|
| **`isOpen`** | 是否启用通知。 |
| **`type`** | **`INotification.SYSTEM_NOTIFICATION`** 或 **`INotification.CUSTOM_NOTIFICATION`**。 |
| **`config`** | **`NotificationConfig.create { }`**：点击跳转 Activity、Bundle、`PendingIntent` 模式、按钮图标与文案等。 |
| **`factory`** | 自定义通知 UI 工厂（可选）。 |

**示例：点击通知跳到播放页（Broadcast 模式，与库内 Receiver 配合）：**

```kotlin
val notificationConfig = NotificationConfig.create {
    targetClass { "com.example.app.PlaybackActionReceiver" }
    targetClassBundle {
        Bundle().apply {
            putString("targetClass", "com.example.app.NowPlayingActivity")
        }
    }
    pendingIntentMode { NotificationConfig.MODE_BROADCAST }
    smallIconRes { R.drawable.ic_notification }
}

StarrySkyInstall.init(this) {
    notification {
        isOpen = true
        type = INotification.CUSTOM_NOTIFICATION
        config = notificationConfig
    }
}.apply()
```

**运行时切换系统 / 自定义通知：**

```kotlin
StarrySky.changeNotification(INotification.SYSTEM_NOTIFICATION)
```

**显隐：**

```kotlin
StarrySky.closeNotification()
StarrySky.openNotification()
```

---

## 7. 封面与图片加载（`image { }`）

通知栏等位置展示封面时，通过 **`ImageLoaderStrategy`** 加载网络图。库内提供 **`GlideImageLoader`**（依赖 **`compileOnly`** Glide，宿主需 **`implementation` Glide**）。

**示例：**

```kotlin
StarrySkyInstall.init(this) {
    image {
        loaderStrategy = GlideImageLoader()
    }
}.apply()
```

若使用 **Coil / Picasso**，可实现 **`ImageLoaderStrategy`** 并在 `image { loaderStrategy = ... }` 中注入。

---

## 8. 拦截器（权限、换链、补全元数据等）

库 **不会** 内置「申请麦克风」等业务权限逻辑；**推荐在 `StarrySkyInterceptor` 中统一处理**：鉴权、补 `songUrl`、换 CDN、拒绝播放等。

- **全局拦截器**：`StarrySkyInstall.init { addInterceptor(...) }` 或 **`StarrySkyInstall.addInterceptor`**。  
- **局部拦截器**：某次播放链上 **`StarrySky.with().addInterceptor(...).playMusic(...)`**，执行完会清理。  
- **线程**：第二个参数 **`InterceptorThread.UI`**（默认）或 **`InterceptorThread.IO`**。

**示例：播放前检查「读音频」权限，未授权则中断**（API 33+ 用 `READ_MEDIA_AUDIO`，更低版本请改用 `READ_EXTERNAL_STORAGE` 等；需 `import android.os.Build`、`android.Manifest`、`androidx.core.content.ContextCompat`。）

```kotlin
class StorageReadInterceptor(private val ctx: Context) : StarrySkyInterceptor() {
    override fun getTag() = "StorageReadInterceptor"

    override fun process(songInfo: SongInfo?, callback: InterceptCallback) {
        if (songInfo == null) {
            callback.onInterrupt("songInfo 为空")
            return
        }
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
            callback.onInterrupt("无读取音频权限")
            return
        }
        callback.onNext(songInfo)
    }
}

// 初始化
StarrySkyInstall.init(this) {
    addInterceptor(StorageReadInterceptor(this), InterceptorThread.UI)
}.apply()
```

**示例：仅本次播放增加日志拦截**

```kotlin
StarrySky.with()
    .addInterceptor(object : StarrySkyInterceptor() {
        override fun getTag() = "LogInterceptor"
        override fun process(songInfo: SongInfo?, callback: InterceptCallback) {
            android.util.Log.i("Play", "will play ${songInfo?.songUrl}")
            callback.onNext(songInfo)
        }
    })
    .playMusicByUrl("https://example.com/a.mp3")
```

---

## 9. 音频焦点（`isAutoManagerFocus`）

- **`true`（默认）**：由库内 **`FocusManager`** 与 Exo 的 **`AudioAttributes`** 协作处理焦点。  
- **`false`**：适合 **多播放器实例** 或自行管理焦点的场景（与 `StarrySkyPlayer` / 多路播放组合时注意互抢焦点）。

```kotlin
StarrySkyInstall.init(this) {
    isAutoManagerFocus = false
}.apply()
```

---

## 10. 自定义播放内核（`playback`）

默认 **`ExoPlayback`**（Media3）。若需自研 **`Playback`** 接口实现：

```kotlin
StarrySkyInstall.init(this) {
    playback = MyPlaybackImplementation(applicationContext)
}.apply()
```

---

## 11. 全局播放阶段监听（`globalPlaybackStageListener`）

在 **任意界面** 统一响应播放阶段（避免每个 Activity 写一遍），例如：播放时暂停其它 SDK 的录音/推拉流。

```kotlin
StarrySkyInstall.init(this) {
    globalPlaybackStageListener = object : GlobalPlaybackStageListener {
        override fun onPlaybackStageChange(stage: PlaybackStage) {
            when (stage.stage) {
                PlaybackStage.PLAYING -> { /* 暂停其它音频相关模块 */ }
                PlaybackStage.PAUSE, PlaybackStage.IDLE -> { /* 恢复 */ }
                PlaybackStage.ERROR -> { /* 提示 stage.errorMsg */ }
            }
        }
    }
}.apply()
```

---

## 12. 播放列表与播放模式

**`SongInfo`** 至少需要 **`songId`**、**`songUrl`**（且 id 在列表中唯一）。

```kotlin
val list = mutableListOf(
    SongInfo("1", "https://example.com/1.mp3", "第一首"),
    SongInfo("2", "https://example.com/2.mp3", "第二首"),
)
StarrySky.with().playMusic(list, 0)

StarrySky.with().setRepeatMode(RepeatMode.REPEAT_MODE_NONE, isLoop = false)
```

**仅 URL 快速播放：**

```kotlin
StarrySky.with().playMusicByUrl("https://example.com/single.mp3")
```

---

## 13. 第二套播放链（`StarrySkyPlayer`）

与 **`StarrySky.with()`** 完全独立：独立 **`PlayerControl`、宿主、通知与缓存配置**。  
**禁止混用**：例如用 `StarrySkyPlayer` 播、`StarrySky.with()` 去暂停。

```kotlin
val aux = StarrySkyPlayer.create(userGlobalConfig = false).apply {
    setOpenCache(false)
    setAutoManagerFocus(false)
}
aux.with().playMusicByUrl("https://example.com/preview.mp3")
aux.with().pauseMusic()
```

---

## 14. 进度与状态监听（UI 侧）

**推荐在 `onResume` 注册进度，`onPause` 移除，并传入稳定 `tag`（如 `toString()`）。**

```kotlin
override fun onResume() {
    super.onResume()
    StarrySky.with().setOnPlayProgressListener(
        object : OnPlayProgressListener {
            override fun onPlayProgress(currPos: Long, duration: Long) {
                // 更新 SeekBar
            }
        },
        tag = toString()
    )
}

override fun onPause() {
    StarrySky.with().removeProgressListener(toString())
    super.onPause()
}
```

**`LiveData` / `Flow` 播放阶段：**

```kotlin
StarrySky.with().playbackState().observe(this) { stage ->
    when (stage.stage) {
        PlaybackStage.PLAYING -> { }
        PlaybackStage.ERROR -> { /* stage.errorMsg */ }
    }
}
```

---

## 15. 音效（均衡器等）

```kotlin
StarrySky.effectSwitch(true)
val session = StarrySky.with().getAudioSessionId()
// 将 session 交给你的均衡器 / 音效 SDK
```

---

## 16. SoundPool

```kotlin
StarrySky.soundPool()?.prepareForAssets(listOf("click.ogg")) { pool ->
    pool.playSound(0)
}
```

（具体 API 以 **`SoundPoolPlayback`** 为准。）

---

## 17. 定时关播（`stopByTimedOff`）

依赖 **`MusicService`** 内计时实现；**`isConnectionService = false`** 时通常为 **no-op**。

```kotlin
// 30 分钟后暂停；若需播完当前曲再停，将第三参设为 true（需 Service 路径）
StarrySky.with().stopByTimedOff(
    time = 30 * 60 * 1000L,
    isPause = true,
    isFinishCurrSong = false
)
```

---

## 18. 释放与解绑

```kotlin
StarrySky.release()
```

退出登录或不再需要播放时调用；会释放全局 `PlayerControl`、解绑 Service（若已绑定）等。

---

## 19. 自检清单

- [ ] JitPack 仓库已加，`implementation` 坐标与 **Tag** 正确  
- [ ] 已调用 **`StarrySkyInstall.init { }.apply()`**  
- [ ] 通知、存储、蓝牙等 **运行时权限** 与隐私政策一致  
- [ ] 网络音频优先 **HTTPS**；HTTP 已配置 cleartext  
- [ ] 未混用 **`StarrySky.with()`** 与 **`StarrySkyPlayer.with()`**  
- [ ] 进度监听 **tag** 与生命周期配对  

更多排错见 **[注意事项与排错](notes-and-faq.md)**。
