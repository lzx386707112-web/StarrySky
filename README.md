# StarrySky

[![](https://img.shields.io/badge/platform-android-green.svg)](http://developer.android.com/index.html)
[![](https://jitpack.io/v/lzx386707112-web/StarrySky.svg)](https://jitpack.io/#lzx386707112-web/StarrySky)
[![](https://img.shields.io/badge/license-MIT-green.svg)](http://choosealicense.com/licenses/mit/)

因为原来的Github账号没法登陆了（https://github.com/EspoirX/StarrySky）现在这个库放到这里维护啦！！

基于 **Kotlin + AndroidX + Media3（ExoPlayer）** 的 Android 音频播放封装：**播放队列、MediaSession、系统/自定义通知、边播边缓存、拦截链、倍速、音效、SoundPool** 等，适合快速接入音乐或长音频业务。

---

## 文档（中文）

**[docs/zh/README.md](docs/zh/README.md)** — 依赖方式、初始化与全部功能说明（缓存、通知、图片加载、拦截器、Service、音效等）及 **代码示例**。

---

## 通过 JitPack 依赖

在根目录 **`settings.gradle`** 的 `dependencyResolutionManagement.repositories` 中加入：

```groovy
maven { url 'https://jitpack.io' }
```

在业务模块 **`build.gradle`**：

```groovy
dependencies {
    implementation 'com.github.lzx386707112-web:StarrySky:Tag'
}
```

将 **`Tag`** 替换为 JitPack 徽章上的版本号（例如 **`2.7.0`**）。流式格式（HLS/DASH 等）需在宿主中按相同 **Media3** 版本追加依赖，见文档 **[集成与各功能说明](docs/zh/installation-and-configuration.md)**。

---

## 最小初始化示例

**默认配置（一行）：** `init` 只写入 `Application`，**必须**再调成员方法 **`apply()`** 完成注册与宿主创建。

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        StarrySkyInstall.init(this).apply()
    }
}
```

**带配置时**使用 **`StarrySkyInstall.init(this) { ... }.apply()`**（lambda 配置 `service` / `cache` / `notification` 等，**末尾仍是 `.apply()`**）。完整示例见 **[docs/zh/installation-and-configuration.md](docs/zh/installation-and-configuration.md)**。

```kotlin
StarrySky.with().playMusicByUrl("https://example.com/audio.mp3")
```

更多配置与示例见 **[docs/zh](docs/zh/README.md)**。

---

## License

MIT License
