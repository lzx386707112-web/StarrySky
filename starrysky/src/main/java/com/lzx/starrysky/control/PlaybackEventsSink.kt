package com.lzx.starrysky.control

import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.starrysky.playback.FocusInfo

/**
 * 播放协调层（[com.lzx.starrysky.manager.PlaybackManager]）对「展示 / 对外分发」的回调契约。
 *
 * 设计意图：
 * - [com.lzx.starrysky.manager.PlaybackManager] 经 [com.lzx.starrysky.manager.PlaybackEngineCallbackBridge] 收到引擎事件后，只应通过本接口把状态往上抛，而不依赖完整的 [PlayerControl]，
 *   从而去掉「Manager 持有 Control、Control 又创建 Manager」在类型上的环形耦合，回调路径单一、职责边界清晰。
 * - 典型实现（[PlayerControl]）中会将事件写入 [kotlinx.coroutines.flow.SharedFlow] 与 [androidx.lifecycle.MutableLiveData] 双通道：新代码优先 collect Flow，旧代码可继续 `observe` LiveData。
 * - 若将来需要单元测试 Manager，可注入假的 Sink，无需构造整个 PlayerControl。
 */
interface PlaybackEventsSink {

    /** 聚合后的播放阶段（含切歌、缓冲、暂停、错误等），由上层写入 LiveData / 全局监听等。 */
    fun onPlaybackStateUpdated(playbackStage: PlaybackStage)

    /** Exo 音频焦点变化，由上层写入 [androidx.lifecycle.MutableLiveData] 等。 */
    fun onFocusStateChange(info: FocusInfo)
}
