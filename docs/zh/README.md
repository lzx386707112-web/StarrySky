# StarrySky 使用文档

基于 **AndroidX + Kotlin + Media3（ExoPlayer）** 的音频播放封装库：队列、MediaSession、通知栏、边播边缓存、拦截链、音效与 SoundPool 等。

---

## 一、通过 JitPack 引入依赖

### 1. 在根目录 `settings.gradle` 加入 JitPack 仓库

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

（若仍使用旧式 `allprojects { repositories { ... } }`，在其中的 `repositories` 里增加 `maven { url 'https://jitpack.io' }` 亦可。）

### 2. 在业务模块 `build.gradle` 中依赖

将 **`Tag`** 换成 JitPack 上显示的版本号（例如 **`2.7.0`**）：

```groovy
dependencies {
    implementation 'com.github.lzx386707112-web:StarrySky:Tag'
}
```

与 **HLS / DASH / SmoothStreaming / RTMP** 等格式相关的 Media3 组件，需在宿主中按与库一致的 **`media3` 版本号** 自行增加 `implementation`（见下文「流式媒体扩展」）。

### 3. Kotlin 与编译参数

- 建议 **JVM 17** 与库模块对齐。  
- 使用 Media3 时建议在模块 `kotlinOptions.freeCompilerArgs` 中加入：

```kotlin
freeCompilerArgs += ["-opt-in=androidx.media3.common.util.UnstableApi"]
```

---

## 二、文档索引

| 文档 | 说明 |
|------|------|
| [集成与各功能说明（含示例）](installation-and-configuration.md) | 初始化、`StarrySkyInstall` 全部配置项：缓存、通知、图片、拦截器、Service、音效、SoundPool 等 |
| [架构说明](architecture.md) | 模块与调用链、双运行时策略 |
| [API 参考与代码片段](api-guide.md) | `StarrySky` / `PlayerControl` / `StarrySkyPlayer` 常用 API |
| [注意事项与排错](notes-and-faq.md) | 明文 HTTP、权限、双实例、进度监听等 |

---

## 三、30 秒最小示例

**`Application`：**

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        StarrySkyInstall.init(this) {
            isDebug = BuildConfig.DEBUG
            service { isConnectionService = false }
            cache {
                isOpen = true
                destFileDir = getExternalFilesDir(null)!!.absolutePath + "/starrysky_cache"
                maxBytes = 512L * 1024 * 1024
            }
            notification {
                isOpen = false
                type = INotification.SYSTEM_NOTIFICATION
            }
            image { loaderStrategy = GlideImageLoader() }
        }.apply()
    }
}
```

**界面中播放：**

```kotlin
StarrySky.with().playMusicByUrl("https://example.com/audio.mp3")
```

完整配置与分功能示例见 **[集成与各功能说明](installation-and-configuration.md)**。
