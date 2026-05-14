package com.lzx.starrysky

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.lzx.starrysky.cache.ICache
import com.lzx.starrysky.control.VoiceEffect
import com.lzx.starrysky.intercept.InterceptorThread
import com.lzx.starrysky.intercept.StarrySkyInterceptor
import com.lzx.starrysky.notification.INotification
import com.lzx.starrysky.notification.NotificationConfig
import com.lzx.starrysky.notification.NotificationManager
import com.lzx.starrysky.notification.imageloader.DefaultImageLoader
import com.lzx.starrysky.notification.imageloader.ImageLoader
import com.lzx.starrysky.notification.imageloader.ImageLoaderStrategy
import com.lzx.starrysky.playback.Playback
import com.lzx.starrysky.service.MusicPlaybackHost
import com.lzx.starrysky.service.MusicRuntimeFactory
import com.lzx.starrysky.service.MusicService
import com.lzx.starrysky.utils.KtPreferences
import com.lzx.starrysky.utils.StarrySkyConstant
import com.lzx.starrysky.utils.isMainProcess
import java.util.WeakHashMap

/**
 * StarrySky 初始化与全局配置入口。
 *
 * ### 配置方式
 * - **最简**：`init(app).apply()` —— 仅设置 [globalContext] 后立刻调用 **`StarrySkyInstall` 的成员函数 `apply()`** 完成注册与宿主创建；中间无配置则使用默认 [StarrySkyInstallSettings]。
 * - **结构化（推荐）**：`init(app) { service { } cache { } ... }.apply()` —— lambda 配置 [settings]，**最后仍须** 调用成员 **`apply()`**。
 * - **链式**：`init(app).setOpenCache(true)...apply()` —— 适合 Java 或简短场景；**最后仍须** 调用成员 **`apply()`**。
 *
 * ### 运行时权限与隐私合规
 * 本库在 manifest 中合并的 `POST_NOTIFICATIONS`、`BLUETOOTH_CONNECT` 等仅表示可选能力所需声明，
 * **不会在 SDK 内部主动发起运行时权限申请**。宿主应在用户同意隐私政策（或单独说明）后，
 * 在合适业务时机自行调用系统权限流程；可用 [com.lzx.starrysky.utils.StarrySkyPermissionChecks] 判断授权状态。
 */
object StarrySkyInstall {

    private var _settings = StarrySkyInstallSettings()

    /**
     * 当前安装配置（分组字段 + 便捷 `service { }` DSL）。
     * [release] 之后会重置为默认实例，请勿长期缓存引用参与业务逻辑。
     */
    val settings: StarrySkyInstallSettings
        get() = _settings

    internal var isDebug
        get() = _settings.isDebug
        set(value) {
            _settings.isDebug = value
        }

    internal var globalContext: Application? = null
    private var retryLineService = 0

    // 服务运行时
    @Volatile
    private var isBindService = false
    private val connectionMap = WeakHashMap<Context, ServiceConnection>()

    @SuppressLint("StaticFieldLeak")
    private var serviceToken: ServiceToken? = null

    @SuppressLint("StaticFieldLeak")
    internal var imageLoader: ImageLoader? = null

    /**
     * 全局播放运行时（Strategy 的统一出口）：Service 路径为 [MusicServiceBinder]，本地路径为 [MusicPlaybackFacade]。
     */
    @SuppressLint("StaticFieldLeak")
    internal var playbackHost: MusicPlaybackHost? = null

    // callback
    @SuppressLint("StaticFieldLeak")
    internal var appLifecycleCallback = AppLifecycleCallback()

    // 音效相关
    internal var voiceEffect = VoiceEffect()

    // region 与历史链式 API / 内部读取兼容的委托属性

    internal val interceptors: MutableList<Pair<StarrySkyInterceptor, String>>
        get() = _settings.interceptors

    internal var isOpenNotification: Boolean
        get() = _settings.notification.isOpen
        set(value) {
            _settings.notification.isOpen = value
        }

    internal var notificationType: Int
        get() = _settings.notification.type
        set(value) {
            _settings.notification.type = value
        }

    internal var notificationConfig: NotificationConfig?
        get() = _settings.notification.config
        set(value) {
            _settings.notification.config = value
        }

    internal var notificationFactory: NotificationManager.NotificationFactory?
        get() = _settings.notification.factory
        set(value) {
            _settings.notification.factory = value
        }

    internal var isOpenCache: Boolean
        get() = _settings.cache.isOpen
        set(value) {
            _settings.cache.isOpen = value
        }

    internal var cacheDestFileDir: String
        get() = _settings.cache.destFileDir
        set(value) {
            _settings.cache.destFileDir = value
        }

    internal var cacheMaxBytes: Long
        get() = _settings.cache.maxBytes
        set(value) {
            _settings.cache.maxBytes = value
        }

    internal var playerCache: ICache?
        get() = _settings.cache.impl
        set(value) {
            _settings.cache.impl = value
        }

    internal var isAutoManagerFocus: Boolean
        get() = _settings.isAutoManagerFocus
        set(value) {
            _settings.isAutoManagerFocus = value
        }

    internal var playback: Playback?
        get() = _settings.playback
        set(value) {
            _settings.playback = value
        }

    internal var globalPlaybackStageListener: GlobalPlaybackStageListener?
        get() = _settings.globalPlaybackStageListener
        set(value) {
            _settings.globalPlaybackStageListener = value
        }

    // endregion

    @JvmStatic
    fun init(application: Application) = apply {
        globalContext = application
    }

    /**
     * 使用分组配置初始化 [globalContext]，并在 lambda 内完成 [settings] 填写；**之后必须调用 `StarrySkyInstall` 的成员函数 `apply()`**。
     */
    @JvmStatic
    fun init(application: Application, configure: StarrySkyInstallSettings.() -> Unit): StarrySkyInstall {
        globalContext = application
        _settings.configure()
        return StarrySkyInstall
    }

    /**
     * 是否debug，区别就是是否打印一些内部 log
     */
    fun setDebug(debug: Boolean) = apply {
        _settings.isDebug = debug
    }

    /**
     * 是否需要后台服务，默认 true，区别是播放器能不能运行在后台。
     *
     * 与 [apply] 中的 Strategy 分支对应：[true] 时走 bindService，由 [MusicServiceBinder] 承载；
     * [false] 时由 [MusicRuntimeFactory.createInProcessHost] 在进程内创建 [MusicPlaybackFacade]。
     */
    fun connService(isConnectionService: Boolean) = apply {
        _settings.service.isConnectionService = isConnectionService
    }

    /**
     * 是否需要 startService，默认false，只有 bindService
     */
    fun isStartService(isStartService: Boolean) = apply {
        _settings.service.isStartService = isStartService
    }

    /**
     * 是否只是 startService 而不需要 startForegroundService，默认true
     */
    fun onlyStartService(onlyStartService: Boolean) = apply {
        _settings.service.onlyStartService = onlyStartService
    }

    /**
     * 连接服务回调，可通过这个监听查看 Service 是否连接成功
     */
    fun connServiceListener(connection: ServiceConnection?) = apply {
        _settings.service.connection = connection
    }

    /**
     * 添加全局拦截器
     */
    fun addInterceptor(interceptor: StarrySkyInterceptor, thread: String = InterceptorThread.UI) = apply {
        _settings.interceptors += Pair(interceptor, thread)
    }

    /**
     * 通知栏开关，打开则显示通知栏，关闭则不显示
     */
    fun setNotificationSwitch(isOpenNotification: Boolean) = apply {
        _settings.notification.isOpen = isOpenNotification
    }

    /**
     * 通知栏类型
     * INotification.SYSTEM_NOTIFICATION
     * INotification.CUSTOM_NOTIFICATION
     * 默认系统通知栏
     */
    fun setNotificationType(notificationType: Int) = apply {
        _settings.notification.type = notificationType
    }

    /**
     * 通知栏其他配置
     */
    fun setNotificationConfig(config: NotificationConfig) = apply {
        _settings.notification.config = config
    }

    /**
     * 自定义通知栏，可参考 NotificationManager 内部的两个默认实现
     */
    fun setNotificationFactory(factory: NotificationManager.NotificationFactory) = apply {
        _settings.notification.factory = factory
    }

    /**
     * 自定义图片加载
     */
    fun setImageLoader(loader: ImageLoaderStrategy) = apply {
        _settings.image.loaderStrategy = loader
    }

    /**
     * 是否开启缓存功能
     */
    fun setOpenCache(open: Boolean) = apply {
        _settings.cache.isOpen = open
    }

    /**
     * 自定义缓存实现
     */
    fun setCache(cache: ICache) = apply {
        _settings.cache.impl = cache
    }

    /**
     * 设置缓存路径
     */
    fun setCacheDestFileDir(cacheDestFileDir: String) = apply {
        _settings.cache.destFileDir = cacheDestFileDir
    }

    /**
     * 设置最大缓存大小
     */
    fun setCacheMaxBytes(cacheMaxBytes: Long) = apply {
        _settings.cache.maxBytes = cacheMaxBytes
    }

    /**
     * 是否自动焦点管理
     */
    fun setAutoManagerFocus(isAutoManagerFocus: Boolean) = apply {
        _settings.isAutoManagerFocus = isAutoManagerFocus
    }

    /**
     * 自定义播放器实现
     */
    fun setPlayback(playback: Playback) = apply {
        _settings.playback = playback
    }

    /**
     * 设置全局状态监听器
     */
    fun setGlobalPlaybackStageListener(listener: GlobalPlaybackStageListener) = apply {
        _settings.globalPlaybackStageListener = listener
    }

    /**
     * 是否使用 WorkManager
     */
    fun startForegroundByWorkManager(value: Boolean) = apply {
        _settings.service.startForegroundByWorkManager = value
    }

    /**
     * 初始化
     */
    fun apply() {
        if (globalContext == null) {
            throw NullPointerException("context is null")
        }
        if (!globalContext!!.isMainProcess()) return // 不是主进程 return

        globalContext!!.registerActivityLifecycleCallbacks(appLifecycleCallback)

        KtPreferences.init(globalContext)
        StarrySkyConstant.KEY_CACHE_SWITCH = isOpenCache // 记录缓存开关状态

        imageLoader = ImageLoader(globalContext)
        val strategy = _settings.image.loaderStrategy
        if (strategy == null) {
            imageLoader?.init(DefaultImageLoader())
        } else {
            imageLoader?.init(strategy)
        }

        if (_settings.service.isConnectionService) {
            // Service 路径：运行时由 onServiceConnected 注入，类型为 IBinder 侧的 [MusicServiceBinder]（实现 [MusicPlaybackHost]）
            bindService()
        } else {
            // 进程内路径：不持有 Binder，仅使用 [MusicPlaybackFacade]；定时关闭无 Service 实现（见 [TimedOffHandlerFactory]）
            playbackHost = MusicRuntimeFactory.createInProcessHost(globalContext!!)
            playbackHost?.startForegroundByWorkManager(_settings.service.startForegroundByWorkManager)
            playbackHost?.setPlayerCache(playerCache, cacheDestFileDir, cacheMaxBytes)
            playbackHost?.setAutoManagerFocus(isAutoManagerFocus)
            playbackHost?.initPlaybackManager(playback)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        /**
         * 连接成功后把 Framework 返回的 [IBinder] 转为 [MusicPlaybackHost]，并在此完成与 [apply] 中「本地分支」一致的装配顺序。
         */
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                if (service is MusicPlaybackHost) {
                    retryLineService = 0
                    playbackHost = service
                    playbackHost?.startForegroundByWorkManager(_settings.service.startForegroundByWorkManager)
                    playbackHost?.setNotificationConfig(
                        isOpenNotification,
                        notificationType,
                        notificationConfig,
                        notificationFactory
                    )
                    playbackHost?.setPlayerCache(
                        playerCache,
                        cacheDestFileDir,
                        cacheMaxBytes
                    )
                    playbackHost?.setAutoManagerFocus(isAutoManagerFocus)
                    playbackHost?.initPlaybackManager(playback)
                    isBindService = true
                    _settings.service.connection?.onServiceConnected(name, service)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBindService = false
            _settings.service.connection?.onServiceDisconnected(name)
            if (retryLineService < 3) {
                retryLineService++
                bindService() // 断开后自动再 bindService
            }
        }
    }

    /**
     * 绑定服务
     */
    @JvmStatic
    fun bindService() {
        try {
            if (isBindService || globalContext == null) return
            val contextWrapper = ContextWrapper(globalContext)
            val intent = Intent(contextWrapper, MusicService::class.java)
            if (_settings.service.isStartService) {
                if (globalContext!!.applicationInfo.targetSdkVersion >= 26 && Build.VERSION.SDK_INT >= 26) {
                    try {
                        contextWrapper.startService(intent)
                    } catch (ex: Exception) {
                        if (!_settings.service.onlyStartService) {
                            intent.putExtra("flag_must_to_show_notification", true)
                            contextWrapper.startForegroundService(intent)
                        }
                        ex.printStackTrace()
                    }
                } else {
                    contextWrapper.startService(intent)
                }
            }
            val result = contextWrapper.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (result) {
                connectionMap[contextWrapper] = serviceConnection
                serviceToken = ServiceToken(contextWrapper)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     * 解邦服务
     */
    @JvmStatic
    fun unBindService() {
        try {
            if (serviceToken == null || !isBindService) {
                return
            }
            val contextWrapper = serviceToken?.wrappedContext
            val conn = connectionMap.getOrDefault(contextWrapper, null)
            conn?.let {
                contextWrapper?.unbindService(conn)
                if (_settings.service.isStartService) {
                    val intent = Intent(contextWrapper, MusicService::class.java)
                    contextWrapper?.stopService(intent)
                }
                isBindService = false
                if (connectionMap.isEmpty()) {
                    playbackHost = null
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    class ServiceToken(var wrappedContext: ContextWrapper)

    @JvmStatic
    fun release() {
        globalContext?.unregisterActivityLifecycleCallbacks(appLifecycleCallback)
        unBindService()
        imageLoader = null
        playerCache?.release()
        serviceToken = null
        playbackHost = null
        globalContext = null
        connectionMap.clear()
        _settings = StarrySkyInstallSettings()
    }
}
