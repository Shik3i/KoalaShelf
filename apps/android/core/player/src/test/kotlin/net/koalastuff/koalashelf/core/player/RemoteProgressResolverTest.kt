package net.koalastuff.koalashelf.core.player

import net.koalastuff.koalashelf.core.network.MediaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProgressResolverTest {

    private val bookId = "book-123"
    private val defaultProgress = MediaProgress(
        id = "prog-1",
        libraryItemId = bookId,
        currentTime = 120.0,
        duration = 1000.0,
        progress = 0.12,
        isFinished = false,
    )

    @Test
    fun `ignores when no book is currently loaded`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = null,
            currentTime = 100.0,
            duration = 1000.0,
            isPlaying = false,
            progress = defaultProgress,
        )
        assertEquals(RemoteProgressResolver.Action.Ignore, action)
    }

    @Test
    fun `ignores when progress event is for a different book`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = "other-book",
            currentTime = 100.0,
            duration = 1000.0,
            isPlaying = false,
            progress = defaultProgress,
        )
        assertEquals(RemoteProgressResolver.Action.Ignore, action)
    }

    @Test
    fun `ignores when player is currently playing`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = bookId,
            currentTime = 100.0,
            duration = 1000.0,
            isPlaying = true,
            progress = defaultProgress.copy(currentTime = 500.0),
        )
        assertEquals(RemoteProgressResolver.Action.Ignore, action)
    }

    @Test
    fun `pauses and moves to end when remote progress marks book finished`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = bookId,
            currentTime = 100.0,
            duration = 1000.0,
            isPlaying = false,
            progress = defaultProgress.copy(isFinished = true, currentTime = 1000.0),
        )
        assertTrue(action is RemoteProgressResolver.Action.FinishAndPause)
        assertEquals(1000.0, (action as RemoteProgressResolver.Action.FinishAndPause).duration, 0.001)
    }

    @Test
    fun `ignores minor time drifts below 2 seconds`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = bookId,
            currentTime = 120.0,
            duration = 1000.0,
            isPlaying = false,
            progress = defaultProgress.copy(currentTime = 121.2), // only 1.2s difference
        )
        assertEquals(RemoteProgressResolver.Action.Ignore, action)
    }

    @Test
    fun `seeks to new remote position when difference is 2 seconds or greater`() {
        val action = RemoteProgressResolver.resolve(
            currentBookId = bookId,
            currentTime = 120.0,
            duration = 1000.0,
            isPlaying = false,
            progress = defaultProgress.copy(currentTime = 250.0),
        )
        assertTrue(action is RemoteProgressResolver.Action.SeekTo)
        assertEquals(250.0, (action as RemoteProgressResolver.Action.SeekTo).targetTime, 0.001)
    }
}
