package net.koalastuff.koalashelf.core.player

import net.koalastuff.koalashelf.core.network.MediaProgress
import kotlin.math.abs

/**
 * Pure Kotlin resolver determining how live remote progress events from Audiobookshelf
 * should update local player state.
 */
object RemoteProgressResolver {
    sealed interface Action {
        data object Ignore : Action
        data class FinishAndPause(val duration: Double) : Action
        data class SeekTo(val targetTime: Double) : Action
    }

    fun resolve(
        currentBookId: String?,
        currentTime: Double,
        duration: Double,
        isPlaying: Boolean,
        progress: MediaProgress,
    ): Action {
        if (currentBookId == null || currentBookId != progress.libraryItemId || isPlaying) {
            return Action.Ignore
        }
        if (progress.isFinished) {
            return Action.FinishAndPause(duration)
        }
        if (abs(progress.currentTime - currentTime) < 2.0) {
            return Action.Ignore
        }
        return Action.SeekTo(progress.currentTime)
    }
}
