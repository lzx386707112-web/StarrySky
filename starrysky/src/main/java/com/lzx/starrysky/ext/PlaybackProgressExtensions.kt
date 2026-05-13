@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lzx.starrysky.ext

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 与 [com.lzx.starrysky.utils.TimerTaskManager] 并列的**可选**扩展：不替代库内默认实现，供业务按需选用。
 *
 * - **[launchProgressPolling]**：任意 [CoroutineScope]（如 `viewModelScope`）上的固定间隔轮询。
 * - **[launchProgressPollingWhenStarted]**：随 Activity/Fragment 前后台启停，等价于常见 `STARTED` 语义下的轮询（基于 ON_START / ON_STOP）。
 * - **[launchPlaybackPositionSampling]**：在同样语义下对 Media3 [Player] 读数抽样；[durationMs] 未就绪时可能为 [C.TIME_UNSET]。
 *
 * 更省电、更贴播放时间轴时，可在业务层用 [Player.addListener] 结合事件 + 自行节流（此处不封装）。
 */

/**
 * 在 [CoroutineScope] 内按固定间隔执行 [tick]；取消返回的 [Job] 即停止。
 */
fun CoroutineScope.launchProgressPolling(
    intervalMs: Long,
    initialDelayMs: Long = 0L,
    tick: suspend () -> Unit
): Job = launch {
    if (initialDelayMs > 0) delay(initialDelayMs)
    val step = intervalMs.coerceAtLeast(1L)
    while (isActive) {
        tick()
        delay(step)
    }
}

/**
 * 仅在界面至少处于 [Lifecycle.State.STARTED] 时运行 [tick]；进入 ON_STOP 暂停轮询，回到 ON_START 重新开始（含 [initialDelayMs]）。
 *
 * 返回的 [Job] 为内部 [SupervisorJob]：取消它可释放观察者与协程；[LifecycleOwner] 销毁时也会自动清理。
 */
fun LifecycleOwner.launchProgressPollingWhenStarted(
    intervalMs: Long = 1000L,
    initialDelayMs: Long = 100L,
    tick: suspend () -> Unit
): Job {
    val supervisor = SupervisorJob()
    val scope = CoroutineScope(supervisor + Dispatchers.Main.immediate)
    val lifecycleRef = this.lifecycle

    val controller = object : DefaultLifecycleObserver {
        private var pollJob: Job? = null

        fun startPoll() {
            pollJob?.cancel()
            pollJob = scope.launch {
                if (initialDelayMs > 0) delay(initialDelayMs)
                val step = intervalMs.coerceAtLeast(1L)
                while (isActive) {
                    tick()
                    delay(step)
                }
            }
        }

        fun stopPoll() {
            pollJob?.cancel()
            pollJob = null
        }

        override fun onStart(owner: LifecycleOwner) {
            startPoll()
        }

        override fun onStop(owner: LifecycleOwner) {
            stopPoll()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            stopPoll()
            lifecycleRef.removeObserver(this)
            supervisor.cancel()
        }
    }

    lifecycleRef.addObserver(controller)
    if (lifecycleRef.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        controller.startPoll()
    }

    return scope.launch {
        try {
            awaitCancellation()
        } finally {
            lifecycleRef.removeObserver(controller)
            controller.stopPoll()
            supervisor.cancel()
        }
    }
}

/**
 * 在 [LifecycleOwner] 前后台语义下，按间隔读取 [Player] 进度并交给 [onSample]（主线程协程内执行）。
 */
fun Player.launchPlaybackPositionSampling(
    lifecycleOwner: LifecycleOwner,
    intervalMs: Long = 500L,
    initialDelayMs: Long = 0L,
    onSample: suspend (player: Player, positionMs: Long, durationMs: Long, bufferedMs: Long, playWhenReady: Boolean) -> Unit
): Job {
    val player = this
    return lifecycleOwner.launchProgressPollingWhenStarted(
        intervalMs = intervalMs,
        initialDelayMs = initialDelayMs
    ) {
        onSample(
            player,
            player.currentPosition,
            player.duration,
            player.bufferedPosition,
            player.playWhenReady
        )
    }
}
