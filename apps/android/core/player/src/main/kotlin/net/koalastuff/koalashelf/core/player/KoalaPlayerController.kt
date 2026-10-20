package net.koalastuff.koalashelf.core.player

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack as AndroidAudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.repository.DownloadRepository
import net.koalastuff.koalashelf.core.data.repository.LibraryRepository
import net.koalastuff.koalashelf.core.data.repository.ProgressRepository
import net.koalastuff.koalashelf.core.data.repository.QueueRepository
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.data.util.SleepForecast
import net.koalastuff.koalashelf.core.data.util.clampRate
import net.koalastuff.koalashelf.core.data.util.forecastSleepWindow
import net.koalastuff.koalashelf.core.data.util.formatEnglishWeekday
import net.koalastuff.koalashelf.core.data.util.formatLocalDay
import net.koalastuff.koalashelf.core.data.util.inNightWindow
import net.koalastuff.koalashelf.core.data.util.smartRewindSeconds
import net.koalastuff.koalashelf.core.network.AbsError
import net.koalastuff.koalashelf.core.network.AudioTrack
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Chapter
import net.koalastuff.koalashelf.core.network.FilterGroup
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.LocalSessionPayload
import net.koalastuff.koalashelf.core.network.MediaProgress
import net.koalastuff.koalashelf.core.network.PlaybackSession
import net.koalastuff.koalashelf.core.network.ProgressUpdate
import net.koalastuff.koalashelf.core.network.SessionSync
import net.koalastuff.koalashelf.core.network.encodeFilter
import java.time.LocalTime
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

sealed interface SleepTimerMode {
    data object Off : SleepTimerMode
    data object EndOfChapter : SleepTimerMode
    data class Minutes(val minutes: Int, val endsAtMillis: Long) : SleepTimerMode
    data class SmartWindow(
        val minMinutes: Int,
        val maxMinutes: Int,
        val minAtMillis: Long,
        val maxAtMillis: Long,
    ) : SleepTimerMode
}

enum class PlayerErrorState {
    RETRYING,
    FATAL,
}

data class PlayerToastEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Core Audio Player Controller (`apps/web/src/lib/player/player.svelte.ts`).
 * Manages ExoPlayer, multi-track global timeline mapping, 15s server progress sync,
 * offline session recording, Smart Rewind, Jump Back, Sleep Timer (Minutes, Chapter, Smart Window),
 * Per-book Speed, Bookmarks, Equalizer/Voice Boost, and Vinyl Crackle / Needle Drop cues.
 */
@OptIn(UnstableApi::class)
class KoalaPlayerController(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val prefsRepository: PrefsRepository,
    private val progressRepository: ProgressRepository,
    private val queueRepository: QueueRepository,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var crackleJob: Job? = null

    private val _item = MutableStateFlow<LibraryItem?>(null)
    val item: StateFlow<LibraryItem?> = _item.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _loadingId = MutableStateFlow<String?>(null)
    val loadingId: StateFlow<String?> = _loadingId.asStateFlow()

    private val _rate = MutableStateFlow(1.0f)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    private val _sleep = MutableStateFlow<SleepTimerMode>(SleepTimerMode.Off)
    val sleep: StateFlow<SleepTimerMode> = _sleep.asStateFlow()

    private val _sleepRemainingSeconds = MutableStateFlow(0.0)
    val sleepRemainingSeconds: StateFlow<Double> = _sleepRemainingSeconds.asStateFlow()

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    private val _lastJump = MutableStateFlow<Double?>(null)
    val lastJump: StateFlow<Double?> = _lastJump.asStateFlow()

    private val _error = MutableStateFlow<PlayerErrorState?>(null)
    val error: StateFlow<PlayerErrorState?> = _error.asStateFlow()

    private val _toastEvents = MutableSharedFlow<PlayerToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<PlayerToastEvent> = _toastEvents.asSharedFlow()

    private var activeSession: PlaybackSession? = null
    private var activeLocalSession: LocalSessionPayload? = null
    private var tracks: List<AudioTrack> = emptyList()
    private var pausedAtMillis: Long = 0L
    private var sleepPausedAtMillis: Long? = null
    private var listenedSecondsAccum: Double = 0.0
    private var lastTickMillis: Long = 0L
    private var playRequestGen: Int = 0
    private var retryCount: Int = 0
    private var tickJob: Job? = null
    private var syncJob: Job? = null

    init {
        sessionRepository.onLeave {
            scope.launch {
                leaveAccount()
            }
        }
        scope.launch {
            sessionRepository.liveSocket.progressEvents.collect { event ->
                val p = event.data ?: return@collect
                if (event.sessionId != null && event.sessionId == currentSessionId) return@collect
                applyRemoteProgress(p)
            }
        }
    }

    val currentSessionId: String?
        get() = activeSession?.id ?: activeLocalSession?.id

    val currentChapter: Chapter?
        get() {
            val t = _currentTime.value
            val list = _chapters.value
            return list.find { t >= it.start && t < it.end } ?: list.lastOrNull()
        }

    val currentChapterIndex: Int
        get() {
            val ch = currentChapter ?: return -1
            return _chapters.value.indexOf(ch)
        }

    val sleepForecast: SleepForecast?
        get() {
            val s = _sleep.value as? SleepTimerMode.SmartWindow ?: return null
            val base = sleepPausedAtMillis ?: System.currentTimeMillis()
            return forecastSleepWindow(
                chapters = _chapters.value,
                currentTime = _currentTime.value,
                rate = _rate.value,
                minLeftSeconds = (s.minAtMillis - base) / 1000.0,
                maxLeftSeconds = (s.maxAtMillis - base) / 1000.0,
            )
        }

    fun setExpanded(open: Boolean) {
        _expanded.value = open
    }

    fun getOrCreateExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
                if (isPlaying) {
                    _buffering.value = false
                    _loadingId.value = null
                    retryCount = 0
                    _error.value = null
                    lastTickMillis = System.currentTimeMillis()
                    sleepPausedAtMillis?.let { pausedAt ->
                        shiftSleepBy(System.currentTimeMillis() - pausedAt)
                    }
                    sleepPausedAtMillis = null
                    if (prefsRepository.prefs.value.needleDrop) {
                        playNeedleDropCue()
                    }
                    updateVinylCrackleLoop()
                } else {
                    lastTickMillis = 0L
                    pausedAtMillis = System.currentTimeMillis()
                    if (_sleep.value !is SleepTimerMode.Off) {
                        sleepPausedAtMillis = pausedAtMillis
                    }
                    updateVinylCrackleLoop()
                    scope.launch { syncNow() }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _buffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    applySoundEffects(player)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleBookEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleStreamError()
            }
        })

        exoPlayer = player
        startLoops()
        return player
    }

    private fun startLoops() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(250L)
                onTick()
            }
        }
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                if (_playing.value) {
                    syncNow()
                }
            }
        }
    }

    fun play(itemId: String? = null, startAtSeconds: Double? = null) {
        val targetId = itemId ?: _item.value?.id ?: return
        val reqGen = ++playRequestGen
        _loadingId.value = targetId
        _error.value = null

        // Ensure MediaSessionService is bound/started so Media3 manages foreground notification
        runCatching {
            val serviceIntent = Intent(context, KoalaMediaSessionService::class.java)
            context.startService(serviceIntent)
        }

        scope.launch {
            val p = getOrCreateExoPlayer()
            val prefs = prefsRepository.prefs.value

            // Resume existing session for the same book
            if ((activeSession?.libraryItemId == targetId || activeLocalSession?.libraryItemId == targetId) &&
                _item.value?.id == targetId &&
                p.mediaItemCount > 0
            ) {
                if (startAtSeconds != null) {
                    seek(startAtSeconds)
                } else if (prefs.smartRewind && pausedAtMillis > 0L) {
                    val rewindSec = smartRewindSeconds(System.currentTimeMillis() - pausedAtMillis)
                    if (rewindSec > 0) {
                        skip(-rewindSec.toDouble())
                    }
                }
                pausedAtMillis = 0L
                checkAutoSleepOnStart()
                p.volume = prefs.volume
                p.play()
                return@launch
            }

            val isKnownFinished = progressRepository.get(targetId)?.isFinished == true
            val presetStart = startAtSeconds
                ?: if (isKnownFinished) {
                    0.0
                } else if (_item.value?.id == targetId && activeSession == null && activeLocalSession == null) {
                    _currentTime.value
                } else {
                    null
                }

            closeCurrentSession()
            if (reqGen != playRequestGen) return@launch
            _buffering.value = true

            val localFiles = downloadRepository.localTrackFiles(targetId)
            val api = sessionRepository.apiClient

            val serverSession = if (api != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        api.play(targetId, sessionRepository.deviceInfo())
                    }.getOrNull()
                }
            } else {
                null
            }

            if (reqGen != playRequestGen) {
                if (serverSession != null && api != null) {
                    withContext(Dispatchers.IO) { runCatching { api.closeSession(serverSession.id) } }
                }
                return@launch
            }

            if (serverSession != null) {
                startOnlinePlayback(serverSession, localFiles, presetStart, isKnownFinished, startAtSeconds != null)
            } else if (localFiles != null && downloadRepository.has(targetId)) {
                startOfflinePlayback(targetId, localFiles, presetStart)
            } else {
                _error.value = PlayerErrorState.FATAL
                _buffering.value = false
                _loadingId.value = null
            }
        }
    }

    private suspend fun startOnlinePlayback(
        s: PlaybackSession,
        localFiles: Map<Int, java.io.File>?,
        presetStart: Double?,
        isKnownFinished: Boolean,
        explicitStart: Boolean,
    ) {
        val libItem = s.libraryItem ?: withContext(Dispatchers.IO) {
            sessionRepository.apiClient?.item(s.libraryItemId)
        } ?: return

        activeSession = s
        activeLocalSession = null
        queueRepository.remove(libItem.id)
        tracks = s.audioTracks.sortedBy { it.startOffset }.ifEmpty { libItem.resolvedTracks() }
        _item.value = libItem
        libItem.userMediaProgress?.let { progressRepository.mergeRemote(it) }

        val finished = isKnownFinished || (libItem.userMediaProgress?.isFinished == true)
        _chapters.value = s.chapters.ifEmpty { libItem.media.chapters }
        _duration.value = if (s.duration > 0.0) s.duration else libItem.durationSeconds

        var start = if (finished && !explicitStart) 0.0 else (presetStart ?: s.currentTime)
        if (start >= _duration.value - 2.0) start = 0.0

        _currentTime.value = start
        listenedSecondsAccum = 0.0
        _lastJump.value = null

        applyBookSpeed(libItem.id)
        loadBookmarksFor(libItem.id)
        prepareExoPlayerTracks(libItem, localFiles, start)
        checkAutoSleepOnStart()
        getOrCreateExoPlayer().play()
    }

    private fun startOfflinePlayback(
        itemId: String,
        localFiles: Map<Int, java.io.File>,
        presetStart: Double?,
    ) {
        val downloaded = downloadRepository.books.value[itemId] ?: return
        val libItem = downloaded.item
        queueRepository.remove(itemId)
        tracks = downloaded.tracks.map { t ->
            AudioTrack(
                index = t.index,
                startOffset = t.startOffset,
                duration = t.duration,
                title = libItem.title,
                contentUrl = "",
                mimeType = t.mimeType,
            )
        }
        _item.value = libItem
        _chapters.value = libItem.media.chapters
        _duration.value = libItem.durationSeconds

        val knownProgress = progressRepository.get(itemId) ?: libItem.userMediaProgress
        val finished = knownProgress?.isFinished == true
        var start = presetStart ?: if (finished) 0.0 else (knownProgress?.currentTime ?: 0.0)
        if (start >= _duration.value - 2.0) start = 0.0

        _currentTime.value = start
        listenedSecondsAccum = 0.0
        _lastJump.value = null

        val now = System.currentTimeMillis()
        activeSession = null
        activeLocalSession = LocalSessionPayload(
            id = UUID.randomUUID().toString(),
            libraryItemId = itemId,
            libraryId = libItem.libraryId,
            displayTitle = libItem.title,
            displayAuthor = libItem.author,
            duration = _duration.value,
            deviceInfo = sessionRepository.deviceInfo(),
            startTime = start,
            currentTime = start,
            timeListening = 0,
            startedAt = now,
            updatedAt = now,
            date = formatLocalDay(now),
            dayOfWeek = formatEnglishWeekday(now),
        )

        applyBookSpeed(itemId)
        prepareExoPlayerTracks(libItem, localFiles, start)
        checkAutoSleepOnStart()
        getOrCreateExoPlayer().play()
    }

    private fun prepareExoPlayerTracks(
        libItem: LibraryItem,
        localFiles: Map<Int, java.io.File>?,
        startSeconds: Double,
    ) {
        val p = getOrCreateExoPlayer()
        val api = sessionRepository.apiClient
        val coverUri = downloadRepository.localCoverFile(libItem.id)?.let { Uri.fromFile(it) }
            ?: api?.coverUrl(libItem.id, width = 512, updatedAt = libItem.updatedAt)?.let { Uri.parse(it) }

        val mediaItems = tracks.map { track ->
            val uri = localFiles?.get(track.index)?.let { Uri.fromFile(it) }
                ?: Uri.parse(api?.trackUrl(track.contentUrl) ?: "")
            val metadata = MediaMetadata.Builder()
                .setTitle(libItem.title)
                .setArtist(libItem.author)
                .setAlbumTitle(currentChapter?.title ?: libItem.seriesLabel.ifBlank { libItem.title })
                .setArtworkUri(coverUri)
                .build()
            MediaItem.Builder()
                .setMediaId("${libItem.id}:${track.index}")
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        }

        val targetTrackIdx = trackIndexAt(startSeconds)
        val trackOffsetMs = ((startSeconds - (tracks.getOrNull(targetTrackIdx)?.startOffset ?: 0.0)).coerceAtLeast(0.0) * 1000.0).roundToLong()

        p.setMediaItems(mediaItems, targetTrackIdx, trackOffsetMs)
        p.playbackParameters = PlaybackParameters(_rate.value)
        p.volume = prefsRepository.prefs.value.volume
        p.prepare()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun toggle() {
        if (_playing.value) pause() else play()
    }

    fun seek(timeSeconds: Double) {
        val dur = _duration.value
        val clamped = timeSeconds.coerceIn(0.0, max(0.0, dur - 0.5))
        _currentTime.value = clamped
        val p = exoPlayer ?: return
        if (tracks.isEmpty() || p.mediaItemCount == 0) return
        val idx = trackIndexAt(clamped)
        val offsetMs = ((clamped - tracks[idx].startOffset).coerceAtLeast(0.0) * 1000.0).roundToLong()
        p.seekTo(idx, offsetMs)
    }

    /**
     * Deliberate jump (chapter, bookmark, timeline, go-to-time): offers undo if >= 30s (`jump` in `player.svelte.ts`).
     */
    fun jump(timeSeconds: Double, jumpedLabelFormat: ((Double) -> String)? = null, undoLabel: String = "Back") {
        val from = _currentTime.value
        seek(timeSeconds)
        if (abs(_currentTime.value - from) < 30.0) return
        _lastJump.value = from
        if (jumpedLabelFormat != null) {
            _toastEvents.tryEmit(
                PlayerToastEvent(
                    message = jumpedLabelFormat(from),
                    actionLabel = undoLabel,
                    onAction = { undoJump() },
                ),
            )
        }
    }

    fun undoJump() {
        val prev = _lastJump.value ?: return
        _lastJump.value = _currentTime.value
        seek(prev)
    }

    fun skip(deltaSeconds: Double) {
        seek(_currentTime.value + deltaSeconds)
    }

    fun back() {
        skip(-prefsRepository.prefs.value.skipBack.toDouble())
    }

    fun forward() {
        skip(prefsRepository.prefs.value.skipForward.toDouble())
    }

    fun prevChapter() {
        val ch = currentChapter ?: run {
            seek(0.0)
            return
        }
        val idx = _chapters.value.indexOf(ch)
        if (_currentTime.value - ch.start > 3.0 || idx <= 0) {
            seek(ch.start)
        } else {
            seek(_chapters.value[idx - 1].start)
        }
    }

    fun nextChapter() {
        val idx = currentChapterIndex
        if (idx >= 0 && idx < _chapters.value.lastIndex) {
            seek(_chapters.value[idx + 1].start)
        }
    }

    fun handleMediaKeyPrevious() {
        if (prefsRepository.prefs.value.mediaKeys == "chapter") prevChapter() else back()
    }

    fun handleMediaKeyNext() {
        if (prefsRepository.prefs.value.mediaKeys == "chapter") nextChapter() else forward()
    }

    fun setRate(newRate: Float) {
        val r = clampRate(newRate)
        _rate.value = r
        val currentItem = _item.value
        if (currentItem != null && prefsRepository.prefs.value.rememberRate) {
            prefsRepository.setBookRate(currentItem.id, r)
        } else {
            prefsRepository.update { it.copy(defaultRate = r) }
        }
        exoPlayer?.playbackParameters = PlaybackParameters(r)
    }

    private fun applyBookSpeed(itemId: String) {
        val prefs = prefsRepository.prefs.value
        val own = if (prefs.rememberRate) prefsRepository.getBookRate(itemId) else null
        val r = clampRate(own ?: prefs.defaultRate)
        _rate.value = r
        exoPlayer?.playbackParameters = PlaybackParameters(r)
    }

    // --- Sleep Timer ---

    fun setSleep(mode: SleepTimerMode) {
        _sleep.value = mode
        sleepPausedAtMillis = if (!_playing.value && mode !is SleepTimerMode.Off) System.currentTimeMillis() else null
        exoPlayer?.volume = prefsRepository.prefs.value.volume
        updateSleepRemaining()
    }

    fun setSleepMinutes(minutes: Int) {
        if (minutes <= 0) {
            setSleep(SleepTimerMode.Off)
        } else {
            setSleep(
                SleepTimerMode.Minutes(
                    minutes = minutes,
                    endsAtMillis = System.currentTimeMillis() + minutes * 60_000L,
                ),
            )
        }
    }

    fun setSleepSmartWindow(minMinutes: Int, maxMinutes: Int) {
        val now = System.currentTimeMillis()
        prefsRepository.update { it.copy(sleepMin = minMinutes, sleepMax = maxMinutes) }
        setSleep(
            SleepTimerMode.SmartWindow(
                minMinutes = minMinutes,
                maxMinutes = maxMinutes,
                minAtMillis = now + minMinutes * 60_000L,
                maxAtMillis = now + maxMinutes * 60_000L,
            ),
        )
    }

    fun extendSleep(extraMinutes: Int) {
        val extraMs = extraMinutes * 60_000L
        exoPlayer?.volume = prefsRepository.prefs.value.volume
        when (val s = _sleep.value) {
            SleepTimerMode.Off -> setSleepMinutes(extraMinutes)
            is SleepTimerMode.Minutes -> {
                _sleep.value = s.copy(
                    minutes = s.minutes + extraMinutes,
                    endsAtMillis = s.endsAtMillis + extraMs,
                )
            }
            is SleepTimerMode.SmartWindow -> {
                _sleep.value = s.copy(
                    minAtMillis = s.minAtMillis + extraMs,
                    maxAtMillis = s.maxAtMillis + extraMs,
                )
            }
            SleepTimerMode.EndOfChapter -> {
                val chEnd = currentChapter?.end ?: _currentTime.value
                val leftSec = max(0.0, (chEnd - _currentTime.value) / _rate.value)
                val endsAt = System.currentTimeMillis() + (leftSec * 1000.0).roundToLong() + extraMs
                _sleep.value = SleepTimerMode.Minutes(minutes = extraMinutes, endsAtMillis = endsAt)
            }
        }
        updateSleepRemaining()
    }

    private fun shiftSleepBy(deltaMs: Long) {
        when (val s = _sleep.value) {
            is SleepTimerMode.Minutes -> _sleep.value = s.copy(endsAtMillis = s.endsAtMillis + deltaMs)
            is SleepTimerMode.SmartWindow -> _sleep.value = s.copy(
                minAtMillis = s.minAtMillis + deltaMs,
                maxAtMillis = s.maxAtMillis + deltaMs,
            )
            else -> Unit
        }
    }

    private fun checkAutoSleepOnStart() {
        val prefs = prefsRepository.prefs.value
        if (!prefs.autoSleep || _sleep.value !is SleepTimerMode.Off || _playing.value) return
        val nowTime = LocalTime.now()
        val nowMins = nowTime.hour * 60 + nowTime.minute
        if (!inNightWindow(nowMins, prefs.autoSleepFrom, prefs.autoSleepTo)) return

        when (prefs.autoSleepKind) {
            "chapter" -> setSleep(SleepTimerMode.EndOfChapter)
            "smart" -> setSleepSmartWindow(prefs.sleepMin, prefs.sleepMax)
            else -> setSleepMinutes(prefs.autoSleepMinutes)
        }
    }

    // --- Sound & Equalizer ---

    fun refreshSoundSettings() {
        exoPlayer?.let { applySoundEffects(it) }
        updateVinylCrackleLoop()
    }

    fun releaseAudioEffects() {
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }
        runCatching {
            equalizer?.release()
            equalizer = null
        }
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun applySoundEffects(player: ExoPlayer) {
        val prefs = prefsRepository.prefs.value
        player.skipSilenceEnabled = prefs.skipSilence
        player.volume = prefs.volume

        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return

        if (currentAudioSessionId != sessionId) {
            releaseAudioEffects()
            currentAudioSessionId = sessionId
        }

        // LoudnessEnhancer for volume boost (1.0x .. 2.0x -> up to +600 mB) & leveling
        runCatching {
            val needBoost = prefs.boost > 1.01f || prefs.leveling || prefs.soundProfile == "night" || prefs.soundProfile == "commute"
            if (needBoost) {
                val enhancer = loudnessEnhancer ?: LoudnessEnhancer(sessionId).also { loudnessEnhancer = it }
                val boostMb = ((prefs.boost - 1f) * 600f).roundToInt()
                val profileMb = when (prefs.soundProfile) {
                    "night" -> 250
                    "commute" -> 350
                    else -> if (prefs.leveling) 180 else 0
                }
                enhancer.setTargetGain(boostMb + profileMb)
                enhancer.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }
        }

        // Equalizer for Voice Boost / Acoustic Scene Profiles
        runCatching {
            val wantVoice = prefs.voiceBoost || prefs.soundProfile != "off"
            if (wantVoice) {
                val eq = equalizer ?: Equalizer(0, sessionId).also { equalizer = it }
                val numBands = eq.numberOfBands
                val range = eq.bandLevelRange
                val minLevel = range[0]
                val maxLevel = range[1]
                for (b in 0 until numBands) {
                    val band = b.toShort()
                    val centerHz = eq.getCenterFreq(band) / 1000
                    val target = when {
                        // Cut low rumble (< 120 Hz)
                        centerHz < 120 -> (minLevel / 2).toShort()
                        // Presence boost for human voice (1 kHz - 4 kHz)
                        centerHz in 1000..4000 -> (maxLevel / 2).toShort()
                        // Soften harsh highs in Night Whisper profile
                        prefs.soundProfile == "night" && centerHz > 6000 -> (minLevel / 3).toShort()
                        else -> 0.toShort()
                    }
                    eq.setBandLevel(band, target)
                }
                eq.enabled = true
            } else {
                equalizer?.enabled = false
            }
        }
    }

    /**
     * Synthesizes a subtle analog needle drop sound cue when playback starts (`sound.ts`).
     */
    private fun playNeedleDropCue() {
        scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                val numSamples = (sampleRate * 0.18).toInt()
                val pcm = ShortArray(numSamples)
                val rng = Random(System.nanoTime())
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val thump = kotlin.math.sin(2.0 * Math.PI * 65.0 * t) * kotlin.math.exp(-t * 32.0) * 0.35
                    val click = if (i in 400..520) (rng.nextDouble() * 2.0 - 1.0) * 0.25 else 0.0
                    val hiss = (rng.nextDouble() * 2.0 - 1.0) * 0.03 * kotlin.math.exp(-t * 10.0)
                    pcm[i] = ((thump + click + hiss).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AndroidAudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AndroidAudioTrack.MODE_STATIC)
                    .build()
                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    delay(220L)
                } finally {
                    runCatching { track.release() }
                }
            }
        }
    }

    /**
     * Synthesizes warm ambient vinyl surface crackle while playing (`sound.ts`).
     */
    private fun updateVinylCrackleLoop() {
        crackleJob?.cancel()
        crackleJob = null
        val prefs = prefsRepository.prefs.value
        if (!_playing.value || !prefs.vinylCrackle) return

        crackleJob = scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 16000
                val chunkSamples = sampleRate / 4
                val pcm = ShortArray(chunkSamples)
                val rng = Random(System.nanoTime())
                val minBuf = max(chunkSamples * 2, AndroidAudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                val track = AndroidAudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AndroidAudioTrack.MODE_STREAM)
                    .build()
                track.play()
                try {
                    while (isActive && _playing.value && prefsRepository.prefs.value.vinylCrackle) {
                        val gain = prefsRepository.prefs.value.vinylCrackleVolume.coerceIn(0.01f, 0.15f)
                        for (i in 0 until chunkSamples) {
                            val pop = if (rng.nextFloat() < 0.0018f) (rng.nextFloat() * 2f - 1f) * 0.65f else 0f
                            val warmth = (rng.nextFloat() * 2f - 1f) * 0.06f
                            pcm[i] = (((pop + warmth) * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        }
                        track.write(pcm, 0, chunkSamples)
                    }
                } finally {
                    runCatching {
                        track.stop()
                        track.release()
                    }
                }
            }
        }
    }

    // --- Bookmarks ---

    private fun loadBookmarksFor(itemId: String) {
        scope.launch(Dispatchers.IO) {
            val list = runCatching {
                sessionRepository.apiClient?.bookmarks(itemId)?.sortedBy { it.time }
            }.getOrNull() ?: emptyList()
            _bookmarks.value = list
        }
    }

    suspend fun addBookmark(title: String, timeSeconds: Double = _currentTime.value) {
        val currentItem = _item.value ?: return
        val api = sessionRepository.apiClient ?: return
        val roundedTime = kotlin.math.floor(timeSeconds)
        val created = withContext(Dispatchers.IO) {
            api.createBookmark(currentItem.id, roundedTime, title)
        }
        _bookmarks.value = (_bookmarks.value.filterNot { it.time == roundedTime } + created).sortedBy { it.time }
    }

    suspend fun renameBookmark(timeSeconds: Double, newTitle: String) {
        val currentItem = _item.value ?: return
        val api = sessionRepository.apiClient ?: return
        withContext(Dispatchers.IO) {
            api.updateBookmark(currentItem.id, timeSeconds, newTitle)
        }
        _bookmarks.value = _bookmarks.value.map { if (it.time == timeSeconds) it.copy(title = newTitle) else it }
    }

    suspend fun deleteBookmark(timeSeconds: Double) {
        val currentItem = _item.value ?: return
        val api = sessionRepository.apiClient ?: return
        withContext(Dispatchers.IO) {
            api.deleteBookmark(currentItem.id, timeSeconds)
        }
        _bookmarks.value = _bookmarks.value.filterNot { it.time == timeSeconds }
    }

    // --- Internals & Progress Sync ---

    private fun trackIndexAt(globalSeconds: Double): Int {
        var idx = 0
        for (i in tracks.indices) {
            if (globalSeconds >= tracks[i].startOffset) idx = i
        }
        return idx
    }

    private fun onTick() {
        val p = exoPlayer ?: return
        if (tracks.isEmpty() || p.mediaItemCount == 0) return

        val trackIdx = p.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[trackIdx]
        val posSec = p.currentPosition.coerceAtLeast(0L) / 1000.0
        _currentTime.value = (track.startOffset + posSec).coerceIn(0.0, max(0.0, _duration.value))

        val now = System.currentTimeMillis()
        if (_playing.value && lastTickMillis > 0L) {
            val dtSec = min(2.0, (now - lastTickMillis) / 1000.0)
            if (dtSec > 0.0) listenedSecondsAccum += dtSec
        }
        lastTickMillis = now

        checkSleepTimerOnTick(p, now)
    }

    private fun updateSleepRemaining() {
        val now = sleepPausedAtMillis ?: System.currentTimeMillis()
        _sleepRemainingSeconds.value = when (val s = _sleep.value) {
            SleepTimerMode.Off -> 0.0
            SleepTimerMode.EndOfChapter -> {
                val ch = currentChapter
                if (ch != null) max(0.0, (ch.end - _currentTime.value) / _rate.value) else 0.0
            }
            is SleepTimerMode.Minutes -> max(0.0, (s.endsAtMillis - now) / 1000.0)
            is SleepTimerMode.SmartWindow -> sleepForecast?.seconds ?: max(0.0, (s.maxAtMillis - now) / 1000.0)
        }
    }

    private fun checkSleepTimerOnTick(p: ExoPlayer, now: Long) {
        val s = _sleep.value
        if (s is SleepTimerMode.Off) {
            _sleepRemainingSeconds.value = 0.0
            return
        }
        updateSleepRemaining()
        if (!_playing.value) return

        val prefs = prefsRepository.prefs.value
        val fadeSec = prefs.sleepFadeDuration.toDouble()

        when (s) {
            SleepTimerMode.EndOfChapter -> {
                val ch = currentChapter ?: return
                val left = (ch.end - _currentTime.value) / _rate.value
                if (_currentTime.value >= ch.end - 0.4) {
                    p.pause()
                    p.volume = prefs.volume
                    _sleep.value = SleepTimerMode.Off
                } else if (prefs.sleepFade && left in 0.0..fadeSec) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    p.volume = prefs.volume * ratio
                }
            }
            is SleepTimerMode.SmartWindow -> {
                val ch = currentChapter
                if (now >= s.minAtMillis && ch != null && _currentTime.value >= ch.end - 0.4) {
                    p.pause()
                    p.volume = prefs.volume
                    _sleep.value = SleepTimerMode.Off
                    return
                }
                val left = (s.maxAtMillis - now) / 1000.0
                if (left <= 0.0) {
                    p.pause()
                    p.volume = prefs.volume
                    if (prefs.sleepRewind > 0) skip(-prefs.sleepRewind.toDouble())
                    _sleep.value = SleepTimerMode.Off
                } else if (prefs.sleepFade && left < fadeSec && sleepForecast?.chapterIndex == null) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    p.volume = prefs.volume * ratio
                }
            }
            is SleepTimerMode.Minutes -> {
                val left = (s.endsAtMillis - now) / 1000.0
                if (left <= 0.0) {
                    p.pause()
                    p.volume = prefs.volume
                    if (prefs.sleepRewind > 0) skip(-prefs.sleepRewind.toDouble())
                    _sleep.value = SleepTimerMode.Off
                } else if (prefs.sleepFade && left < fadeSec) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    p.volume = prefs.volume * ratio
                }
            }
            SleepTimerMode.Off -> Unit
        }
    }

    private fun handleBookEnded() {
        val currentItem = _item.value ?: return
        _currentTime.value = _duration.value
        _playing.value = false

        scope.launch {
            syncNow()
            withContext(Dispatchers.IO) {
                runCatching {
                    sessionRepository.apiClient?.updateProgress(
                        currentItem.id,
                        ProgressUpdate(
                            isFinished = true,
                            currentTime = _duration.value,
                            progress = 1.0,
                            duration = _duration.value,
                        ),
                    )
                }
            }
            val existing = progressRepository.get(currentItem.id)
            progressRepository.setLocal(
                (existing ?: MediaProgress(id = currentItem.id, libraryItemId = currentItem.id, duration = _duration.value)).copy(
                    isFinished = true,
                    currentTime = _duration.value,
                    progress = 1.0,
                    lastUpdate = System.currentTimeMillis(),
                ),
            )
            progressRepository.celebrateFinished(currentItem.id)

            val prefs = prefsRepository.prefs.value
            if (prefs.removeFinished && downloadRepository.has(currentItem.id) && activeLocalSession == null) {
                downloadRepository.remove(currentItem.id)
            }

            val s = _sleep.value
            if (s is SleepTimerMode.EndOfChapter ||
                (s is SleepTimerMode.SmartWindow && System.currentTimeMillis() >= s.minAtMillis)
            ) {
                _sleep.value = SleepTimerMode.Off
                return@launch
            }

            val nextQueued = if (prefs.autoQueue) queueRepository.shift() else null
            if (nextQueued != null) {
                play(nextQueued.id)
            } else if (prefs.seriesOffer) {
                offerNextInSeries(currentItem)
            }
        }
    }

    private suspend fun offerNextInSeries(finishedItem: LibraryItem) {
        val series = finishedItem.media.metadata.parsedSeriesList.firstOrNull() ?: return
        val api = sessionRepository.apiClient ?: return
        val nextBook = withContext(Dispatchers.IO) {
            runCatching {
                val results = api.libraryItems(
                    libraryId = finishedItem.libraryId,
                    limit = 0,
                    filter = encodeFilter(FilterGroup.SERIES, series.id),
                    sort = "sequence",
                ).results
                val idx = results.indexOfFirst { it.id == finishedItem.id }
                if (idx >= 0 && idx + 1 < results.size) results[idx + 1] else null
            }.getOrNull()
        } ?: return

        _toastEvents.tryEmit(
            PlayerToastEvent(
                message = nextBook.title,
                actionLabel = "Play",
                onAction = { play(nextBook.id) },
            ),
        )
    }

    private fun handleStreamError() {
        val s = activeSession
        if (s == null || retryCount >= 3) {
            _error.value = PlayerErrorState.FATAL
            _playing.value = false
            _loadingId.value = null
            return
        }
        retryCount++
        _error.value = PlayerErrorState.RETRYING
        val resumeAt = _currentTime.value
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                sessionRepository.apiClient?.refresh() == true
            }
            val currentItem = _item.value
            if (refreshed && currentItem != null) {
                prepareExoPlayerTracks(
                    libItem = currentItem,
                    localFiles = downloadRepository.localTrackFiles(currentItem.id),
                    startSeconds = resumeAt,
                )
                exoPlayer?.play()
            } else {
                _error.value = PlayerErrorState.FATAL
                _playing.value = false
                _loadingId.value = null
            }
        }
    }

    private suspend fun syncNow() {
        val local = activeLocalSession
        if (local != null) {
            val now = System.currentTimeMillis()
            val listened = listenedSecondsAccum.roundToInt()
            listenedSecondsAccum = 0.0
            val updated = local.copy(
                currentTime = _currentTime.value,
                timeListening = local.timeListening + listened,
                updatedAt = now,
                date = formatLocalDay(now),
            )
            activeLocalSession = updated
            withContext(Dispatchers.IO) {
                downloadRepository.saveLocalSession(updated)
                downloadRepository.flushOfflineSessions()
            }
            updateLocalProgressStore()
            return
        }

        val s = activeSession ?: return
        val api = sessionRepository.apiClient ?: return
        val listened = listenedSecondsAccum.roundToInt()
        val syncBody = SessionSync(
            currentTime = _currentTime.value,
            timeListened = listened,
            duration = _duration.value,
        )
        try {
            withContext(Dispatchers.IO) {
                api.syncSession(s.id, syncBody)
            }
            listenedSecondsAccum = max(0.0, listenedSecondsAccum - listened)
            updateLocalProgressStore()
        } catch (e: AbsError) {
            if (e.status == 404 && _item.value != null) {
                // Server forgot session: reopen at current spot
                val wasPlaying = _playing.value
                val itemId = _item.value!!.id
                val spot = _currentTime.value
                activeSession = null
                play(itemId, spot)
                if (!wasPlaying) pause()
            }
        } catch (_: Exception) {
            // Network glitch: keep accumulated listening time for next sync
        }
    }

    private fun updateLocalProgressStore() {
        val currentItem = _item.value ?: return
        val prev = progressRepository.get(currentItem.id)
        val dur = _duration.value.coerceAtLeast(1.0)
        progressRepository.setLocal(
            MediaProgress(
                id = prev?.id ?: currentItem.id,
                libraryItemId = currentItem.id,
                episodeId = null,
                duration = _duration.value,
                progress = (_currentTime.value / dur).coerceIn(0.0, 1.0),
                currentTime = _currentTime.value,
                isFinished = prev?.isFinished ?: false,
                hideFromContinueListening = false,
                lastUpdate = System.currentTimeMillis(),
                startedAt = prev?.startedAt ?: System.currentTimeMillis(),
                finishedAt = prev?.finishedAt,
            ),
        )
    }

    private suspend fun closeCurrentSession() {
        exoPlayer?.pause()
        val local = activeLocalSession
        if (local != null) {
            syncNow()
            activeLocalSession = null
            return
        }
        val s = activeSession ?: return
        activeSession = null
        val listened = listenedSecondsAccum.roundToInt()
        listenedSecondsAccum = 0.0
        val syncBody = SessionSync(
            currentTime = _currentTime.value,
            timeListened = listened,
            duration = _duration.value,
        )
        withContext(Dispatchers.IO) {
            runCatching { sessionRepository.apiClient?.closeSession(s.id, syncBody) }
        }
    }

    suspend fun leaveAccount() {
        closeCurrentSession()
        _item.value = null
        _chapters.value = emptyList()
        _bookmarks.value = emptyList()
        _expanded.value = false
        _sleep.value = SleepTimerMode.Off
    }

    private fun applyRemoteProgress(p: MediaProgress) {
        val currentItem = _item.value ?: return
        if (currentItem.id != p.libraryItemId || _playing.value) return
        if (p.isFinished) {
            pause()
            _currentTime.value = _duration.value
            return
        }
        if (abs(p.currentTime - _currentTime.value) < 2.0) return
        seek(p.currentTime)
    }

    /**
     * Completely releases player resources, audio effects, and background jobs.
     */
    fun release() {
        releaseAudioEffects()
        crackleJob?.cancel()
        crackleJob = null
        exoPlayer?.run {
            stop()
            release()
        }
        exoPlayer = null
    }
}
