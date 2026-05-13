package com.lzx.starrysky.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * 周期性在主线程执行 [setUpdateProgressTask] 注册的回调（进度刷新、通知栏进度、定时关播倒计时等）。
 *
 * 实现要点：
 * - 使用 [MainLooper] 单线程调度，避免额外 [java.util.concurrent.ScheduledExecutorService] 线程与「后台线程 post 到主线程」的叠套。
 * - **切勿**对共享的 [MainLooper] 调用 `removeCallbacksAndMessages(null)`，否则会误删应用内其它通过该 Handler 投递的任务（历史写法属于严重隐患）。
 * - 停止时仅 [android.os.Handler.removeCallbacks] 移除本类注册的 [scheduleRunnable]，与其它模块隔离。
 *
 * 可选替代/增强（与生命周期或 Media3 对齐的轮询）见包 [com.lzx.starrysky.ext] 中的 [launchProgressPollingWhenStarted]、[launchPlaybackPositionSampling] 等。
 */
class TimerTaskManager : DefaultLifecycleObserver {

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS: Long = 1000L
        private const val PROGRESS_UPDATE_INITIAL_DELAY_MS: Long = 100L
    }

    private val handler = MainLooper.instance

    /** 与 [stopToUpdateProgress] / [removeUpdateProgressTask] 可能在不同线程调用配合使用。 */
    @Volatile
    private var active = false

    @Volatile
    private var intervalMs: Long = PROGRESS_UPDATE_INTERVAL_MS

    private var mUpdateProgressTask: Runnable? = null

    /**
     * 周期性投递到主线程的调度令牌；仅移除本 Runnable，不影响其它 MainLooper 回调。
     */
    private val scheduleRunnable = object : Runnable {
        override fun run() {
            if (!active) return
            mUpdateProgressTask?.run()
            if (active) {
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    /**
     * 开始周期任务：先取消上一轮，再按 [timeInternal] 间隔循环（首次在 [PROGRESS_UPDATE_INITIAL_DELAY_MS] 后触发）。
     */
    fun startToUpdateProgress(timeInternal: Long = PROGRESS_UPDATE_INTERVAL_MS) {
        stopToUpdateProgress()
        intervalMs = timeInternal.coerceAtLeast(1L)
        active = true
        handler.postDelayed(scheduleRunnable, PROGRESS_UPDATE_INITIAL_DELAY_MS)
    }

    /**
     * 设置在主线程上执行的 Runnable（通常为业务方用 Kotlin lambda 创建的单一实例）。
     */
    fun setUpdateProgressTask(task: Runnable?) {
        mUpdateProgressTask = task
    }

    /**
     * 停止周期调度（不置空 task、不 shutdown；可再次 [startToUpdateProgress]）。
     */
    fun stopToUpdateProgress() {
        active = false
        handler.removeCallbacks(scheduleRunnable)
    }

    /**
     * 停止调度并清空 task；适合与 [Lifecycle] 的 ON_DESTROY 或 Service/通知销毁对齐。
     */
    fun removeUpdateProgressTask() {
        stopToUpdateProgress()
        mUpdateProgressTask = null
    }

    /**
     * 是否与 [startToUpdateProgress] 成功启动后的周期一致（在首次延迟触发前为 false）。
     */
    fun isRunning(): Boolean = active

    /**
     * 绑定生命周期，[Lifecycle.Event.ON_DESTROY] 时自动 [removeUpdateProgressTask]。
     */
    fun bindLifecycle(lifecycle: Lifecycle?) = apply {
        lifecycle?.removeObserver(this)
        lifecycle?.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        removeUpdateProgressTask()
    }
}
