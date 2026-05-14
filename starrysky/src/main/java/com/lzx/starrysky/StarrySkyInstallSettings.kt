package com.lzx.starrysky

import android.content.ServiceConnection
import com.lzx.starrysky.cache.ICache
import com.lzx.starrysky.intercept.InterceptorThread
import com.lzx.starrysky.intercept.StarrySkyInterceptor
import com.lzx.starrysky.notification.INotification
import com.lzx.starrysky.notification.NotificationConfig
import com.lzx.starrysky.notification.NotificationManager
import com.lzx.starrysky.notification.imageloader.ImageLoaderStrategy
import com.lzx.starrysky.playback.Playback

/**
 * StarrySky 初始化时的**可配置项**分组，与 [StarrySkyInstall] 上的链式 `setXxx()` 读写同一套数据。
 *
 * 推荐使用 [StarrySkyInstall.init] 的 lambda 重载按模块填写，避免 Application 里出现超长链式调用：
 *
 * ```
 * StarrySkyInstall.init(this) {
 *     isDebug = BuildConfig.DEBUG
 *     service {
 *         startForegroundByWorkManager = true
 *     }
 *     cache {
 *         isOpen = true
 *         destFileDir = path
 *     }
 *     notification {
 *         isOpen = true
 *         type = INotification.CUSTOM_NOTIFICATION
 *         config = notificationConfig
 *     }
 * }.apply()
 * ```
 */
class StarrySkyInstallSettings internal constructor() {

    /** 是否输出内部调试日志 */
    var isDebug: Boolean = true

    /** 后台 [android.app.Service] 与前台策略 */
    val service = ServiceSettings()

    /** 通知栏展示与自定义 */
    val notification = NotificationSettings()

    /** 播放缓存目录、上限与 [ICache] 实现 */
    val cache = CacheSettings()

    /** 通知封面等图片的 [ImageLoaderStrategy] */
    val image = ImageSettings()

    /** 是否由库内自动处理音频焦点 */
    var isAutoManagerFocus: Boolean = true

    /** 自定义 [Playback]；为 null 时使用默认实现 */
    var playback: Playback? = null

    /** 全局播放阶段回调 */
    var globalPlaybackStageListener: GlobalPlaybackStageListener? = null

    /** 全局拦截器列表（与 [StarrySkyInstall.addInterceptor] 操作同一实例） */
    val interceptors = mutableListOf<Pair<StarrySkyInterceptor, String>>()

    /**
     * 配置后台 Service 相关行为。
     *
     * @param isConnectionService 为 false 时在进程内使用 [MusicPlaybackFacade]，不 bind Service。
     */
    fun service(block: ServiceSettings.() -> Unit) {
        service.block()
    }

    fun notification(block: NotificationSettings.() -> Unit) {
        notification.block()
    }

    fun cache(block: CacheSettings.() -> Unit) {
        cache.block()
    }

    fun image(block: ImageSettings.() -> Unit) {
        image.block()
    }

    /** 与 [StarrySkyInstall.addInterceptor] 等价，便于在 [StarrySkyInstall.init] 的 lambda 内调用。 */
    fun addInterceptor(interceptor: StarrySkyInterceptor, thread: String = InterceptorThread.UI) {
        interceptors += Pair(interceptor, thread)
    }
}

class ServiceSettings internal constructor() {
    /**
     * `true`（默认）：通过 bind [com.lzx.starrysky.service.MusicService] 使用远程宿主，连接为异步。
     * [StarrySky.with] 若在 [android.content.ServiceConnection.onServiceConnected] 之前首次创建，
     * 协调层须能解析到 [StarrySkyInstall.playbackHost]（见 [com.lzx.starrysky.manager.PlaybackManager]）。
     *
     * `false`：进程内 [com.lzx.starrysky.service.MusicPlaybackFacade]，无 bind 时序问题；定时关播「播完当前曲再停」
     * 依赖 Service 的路径在无 Service 时为 no-op（见 [com.lzx.starrysky.service.TimedOffHandlerFactory]）。
     */
    var isConnectionService: Boolean = true
    var isStartService: Boolean = false
    var onlyStartService: Boolean = true
    var connection: ServiceConnection? = null
    var startForegroundByWorkManager: Boolean = false
}

class NotificationSettings internal constructor() {
    var isOpen: Boolean = false
    var type: Int = INotification.SYSTEM_NOTIFICATION
    var config: NotificationConfig? = null
    var factory: NotificationManager.NotificationFactory? = null
}

class CacheSettings internal constructor() {
    var isOpen: Boolean = false
    var destFileDir: String = ""
    var maxBytes: Long = 512 * 1024 * 1024
    var impl: ICache? = null
}

class ImageSettings internal constructor() {
    var loaderStrategy: ImageLoaderStrategy? = null
}
