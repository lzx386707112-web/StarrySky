# 集成与初始化

## 1. Gradle 依赖

### 1.1 源码依赖（本仓库）

在宿主 `settings.gradle` 中 `include` 本库模块，在宿主 `build.gradle`：

```groovy
dependencies {
    implementation project(':starrysky')
}
```

宿主需与库协调 **Kotlin JVM 目标**（当前库为 **17**）及 **Media3 `@UnstableApi` opt-in**（可参考 `:app` 的 `kotlinOptions.freeCompilerArgs`）。

### 1.2 远程依赖（JitPack 等）

若使用发布坐标，请参考根目录 [README.md](../../README.md) 中的 JitPack 徽章与说明；**版本号与传递依赖的 Media3 版本以实际发布为准**。

### 1.3 流式与扩展格式

`:starrysky` 已 `api`/`implementation` 引入核心 Media3 组件；宿主可按需增加与 **`rootProject.ext.media3_version`** 一致的扩展，例如（与 `:app` 对齐）：

```groovy
def m3 = rootProject.ext.media3_version
implementation "androidx.media3:media3-exoplayer-hls:$m3"
implementation "androidx.media3:media3-exoplayer-dash:$m3"
implementation "androidx.media3:media3-exoplayer-smoothstreaming:$m3"
implementation "androidx.media3:media3-datasource-rtmp:$m3"
```

## 2. Manifest 合并

库模块 `starrysky/src/main/AndroidManifest.xml` 会合并进宿主，主要包含：

- **`MusicService`**：`foregroundServiceType="mediaPlayback"`，用于 **`isConnectionService = true`** 路径。
- **前台服务 / 网络 / Wi‑Fi / 唤醒锁** 等权限声明。
- **`networkSecurityConfig`**：库内带有一份默认网络安全配置（宿主可被 `tools:replace` 覆盖）。

宿主通常还需：

- **`INTERNET`**（若未由其他库合并）。
- **`POST_NOTIFICATIONS`**（Android 13+ 通知：库仅声明，**需宿主在合适时机申请运行时权限**）。
- 若播放 **明文 HTTP**：在宿主 Application 或 `network-security-config` 中放行 cleartext（见 [注意事项](notes-and-faq.md)）。

## 3. Application 初始化

在 **`Application.onCreate`** 中调用（主进程）：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StarrySkyInstall.init(this) {
            isDebug = BuildConfig.DEBUG

            service {
                isConnectionService = true   // 或 false，见下文
                startForegroundByWorkManager = false
                // isStartService / onlyStartService 按后台策略配置
                // connection = 你的 ServiceConnection（可选）
            }

            cache {
                isOpen = true
                destFileDir = getExternalFilesDir(null)?.absolutePath + "/starrysky_cache"
                maxBytes = 512L * 1024 * 1024
                // impl = 自定义 ICache（可选）
            }

            notification {
                isOpen = true
                type = INotification.CUSTOM_NOTIFICATION
                config = notificationConfig   // NotificationConfig.create { ... }
                // factory = 自定义通知工厂（可选）
            }

            image {
                loaderStrategy = GlideImageLoader()  // 或其它 ImageLoaderStrategy
            }

            isAutoManagerFocus = true   // 是否由库内自动处理音频焦点

            addInterceptor(myInterceptor)   // 全局拦截器（可选）

        }.apply()
    }
}
```

**必须调用 `.apply()`** 完成注册、绑定 Service（若开启）与创建默认运行时。

`StarrySkyInstall.init` 在非主进程会直接 return，避免多进程重复初始化。

## 4. 配置项说明（`StarrySkyInstallSettings`）

### 4.1 根级

| 字段 | 含义 |
|------|------|
| `isDebug` | 是否输出内部调试日志 |
| `isAutoManagerFocus` | 是否自动申请/释放音频焦点（多实例场景可关闭，见示例 `TestApplication`） |
| `playback` | 自定义 `Playback` 实现；为 null 时使用默认 `ExoPlayback` |
| `globalPlaybackStageListener` | 全局播放阶段监听 |

### 4.2 `service { }`（`ServiceSettings`）

| 字段 | 含义 |
|------|------|
| **`isConnectionService`** | `true`：bind `MusicService`；`false`：进程内 `MusicPlaybackFacade`，无 bind 时序问题 |
| `isStartService` / `onlyStartService` | 是否在 bind 前先 `startService` / 前台服务兜底策略 |
| `startForegroundByWorkManager` | Android 12+ 后台启动前台限制时，是否通过 WorkManager 兜底拉起前台通知 |
| `connection` | 可选 `ServiceConnection`，监听 bind 结果 |

### 4.3 `cache { }`（`CacheSettings`）

| 字段 | 含义 |
|------|------|
| `isOpen` | 是否开启边播边缓存（与运行时 `StarrySkyConstant` 开关配合） |
| `destFileDir` | 缓存根目录 |
| `maxBytes` | 缓存上限 |
| `impl` | 自定义 `ICache`（默认库内 `ExoCache` + Media3 `SimpleCache`） |

### 4.4 `notification { }`（`NotificationSettings`）

| 字段 | 含义 |
|------|------|
| `isOpen` | 是否启用通知栏 |
| `type` | `INotification.SYSTEM_NOTIFICATION` 或 `CUSTOM_NOTIFICATION` |
| `config` | `NotificationConfig`：点击跳转、`PendingIntent` 模式等 |
| `factory` | 自定义通知 UI 工厂（可选） |

### 4.5 `image { }`（`ImageSettings`）

| 字段 | 含义 |
|------|------|
| `loaderStrategy` | 封面图加载策略（如 `GlideImageLoader`） |

### 4.6 拦截器

- **`addInterceptor`**（在 lambda 内或 `StarrySkyInstall.addInterceptor`）：全局拦截器，在播放前执行；可指定 `InterceptorThread.UI` 或 `IO`。

## 5. 释放与解绑

- 退出应用或用户登出等场景可调用 **`StarrySky.release()`**：释放 `PlayerControl`、解绑 Service、`StarrySkyInstall.release()` 等。
- 若仅临时解绑：**`StarrySkyInstall.unBindService()`**（需与自身生命周期配合）。

详见源码 `StarrySky.kt`、`StarrySkyInstall.kt`。
