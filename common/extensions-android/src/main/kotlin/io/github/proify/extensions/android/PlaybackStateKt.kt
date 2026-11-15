@file:Suppress("unused")

package io.github.proify.extensions.android

import android.media.session.PlaybackState
import android.os.Bundle

/**
 * 创建 [PlaybackState] 的副本，允许覆盖特定的属性。
 *
 * 由于 [PlaybackState] 本身是不可变的，此方法通过 [PlaybackState.Builder] 重新构建一个新的实例。
 *
 * @param state 当前播放状态，例如 [PlaybackState.STATE_PLAYING]。
 * @param position 当前播放位置（毫秒）。
 * @param bufferedPosition 当前缓冲位置（毫秒）。
 * @param playbackSpeed 当前播放速度。
 * @param actions 当前支持的操作位掩码。
 * @param errorMessage 可读的错误信息。
 * @param updateTime 最后一次更新位置的时间戳。
 * @param activeQueueItemId 当前活动播放队列项目的 ID。
 * @param customActions 自定义操作列表。
 * @param extras 附加的数据元数据。
 * @return 包含更新后属性的新 [PlaybackState] 实例。
 */
fun PlaybackState.copy(
    state: Int = this.state,
    position: Long = this.position,
    bufferedPosition: Long = this.bufferedPosition,
    playbackSpeed: Float = this.playbackSpeed,
    actions: Long = this.actions,
    errorMessage: CharSequence? = this.errorMessage,
    updateTime: Long = this.lastPositionUpdateTime,
    activeQueueItemId: Long = this.activeQueueItemId,
    customActions: List<PlaybackState.CustomAction> = this.customActions ?: emptyList(),
    extras: Bundle? = this.extras
): PlaybackState {
    val builder = PlaybackState.Builder()

    // 核心状态设置
    builder.setState(state, position, playbackSpeed, updateTime)

    // 基础属性映射
    builder.setBufferedPosition(bufferedPosition)
    builder.setActions(actions)
    builder.setActiveQueueItemId(activeQueueItemId)
    builder.setErrorMessage(errorMessage)
    builder.setExtras(extras)

    // 重建自定义操作列表
    customActions.forEach { action ->
        builder.addCustomAction(action)
    }

    return builder.build()
}