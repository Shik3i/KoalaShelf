package net.koalastuff.koalashelf.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.koalastuff.koalashelf.core.designsystem.LocalKoalaHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.repository.QueueRepository
import net.koalastuff.koalashelf.core.data.util.clampRate
import net.koalastuff.koalashelf.core.data.util.clampSleepWindow
import net.koalastuff.koalashelf.core.data.util.formatClock
import net.koalastuff.koalashelf.core.data.util.formatDuration
import net.koalastuff.koalashelf.core.data.util.formatEndsAtClock
import net.koalastuff.koalashelf.core.data.util.formatSpeed
import net.koalastuff.koalashelf.core.data.util.parseClock
import net.koalastuff.koalashelf.core.designsystem.BookCover
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.TurntableDeck
import net.koalastuff.koalashelf.core.designsystem.VinylDisc
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Chapter
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.player.KoalaPlayerController
import net.koalastuff.koalashelf.core.player.SleepTimerMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class PlayerPanel {
    NONE,
    SPEED,
    SLEEP,
    SOUND,
    CHAPTERS,
    BOOKMARKS,
    QUEUE,
    BEDTIME,
}

@Composable
fun MiniPlayerBar(
    playerController: KoalaPlayerController,
    prefs: KoalaPrefs,
    coverUrlProvider: (LibraryItem) -> String?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item by playerController.item.collectAsStateWithLifecycle()
    val playing by playerController.playing.collectAsStateWithLifecycle()
    val currentTime by playerController.currentTime.collectAsStateWithLifecycle()
    val duration by playerController.duration.collectAsStateWithLifecycle()
    val chapters by playerController.chapters.collectAsStateWithLifecycle()

    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    val currentChapter = remember(chapters, currentTime) {
        chapters.firstOrNull { currentTime >= it.start && currentTime < it.end }
            ?: chapters.lastOrNull()
    }
    val progress = if (duration > 0.0) {
        (currentTime / duration).toFloat().coerceIn(0f, 1f)
    } else 0f

    AnimatedVisibility(
        visible = item != null,
        enter = expandVertically(tween(240)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
        modifier = modifier.fillMaxWidth(),
    ) {
        val activeItem = item ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .border(width = 1.dp, color = colors.line)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = colors.accent,
                trackColor = colors.track,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefs.spin) {
                    VinylDisc(
                        title = activeItem.title,
                        author = activeItem.author,
                        coverUrl = coverUrlProvider(activeItem),
                        playing = playing,
                        modifier = Modifier.size(46.dp),
                    )
                } else {
                    BookCover(
                        title = activeItem.title,
                        author = activeItem.author,
                        coverUrl = coverUrlProvider(activeItem),
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 8.dp,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = currentChapter?.title?.takeIf { it.isNotBlank() } ?: activeItem.author
                    Text(
                        text = "$subtitle · ${formatClock(currentTime)} / ${formatClock(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = { playerController.back() }) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = strings.settingsSkipBack,
                        tint = colors.ink,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colors.accent,
                    contentColor = colors.onAccent,
                    modifier = Modifier.size(42.dp),
                    onClick = { playerController.toggle() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.75f, animationSpec = tween(150))) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.75f, animationSpec = tween(120)))
                            },
                            label = "mini_play_pause",
                        ) { isPlayingNow ->
                            Icon(
                                imageVector = if (isPlayingNow) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingNow) strings.playerPause else strings.playerPlay,
                            )
                        }
                    }
                }

                IconButton(onClick = { playerController.forward() }) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = strings.settingsSkipForward,
                        tint = colors.ink,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FullPlayerSheet(
    playerController: KoalaPlayerController,
    prefsRepository: PrefsRepository,
    queueRepository: QueueRepository,
    coverUrlProvider: (LibraryItem) -> String?,
    onDismiss: () -> Unit,
    onOpenBookDetail: (String) -> Unit,
) {
    val item by playerController.item.collectAsStateWithLifecycle()
    val activeItem = item ?: return
    val playing by playerController.playing.collectAsStateWithLifecycle()
    val currentTime by playerController.currentTime.collectAsStateWithLifecycle()
    val duration by playerController.duration.collectAsStateWithLifecycle()
    val chapters by playerController.chapters.collectAsStateWithLifecycle()
    val bookmarks by playerController.bookmarks.collectAsStateWithLifecycle()
    val rate by playerController.rate.collectAsStateWithLifecycle()
    val sleep by playerController.sleep.collectAsStateWithLifecycle()
    val sleepRemaining by playerController.sleepRemainingSeconds.collectAsStateWithLifecycle()
    val lastJump by playerController.lastJump.collectAsStateWithLifecycle()
    val prefs by prefsRepository.prefs.collectAsStateWithLifecycle()
    val silenceSaved by prefsRepository.silenceSavedTotalSec.collectAsStateWithLifecycle()
    val queueItems by queueRepository.items.collectAsStateWithLifecycle()
    val remoteHandoff by playerController.remoteHandoff.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activePanel by remember { mutableStateOf(PlayerPanel.NONE) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }

    val currentChapterIndex = remember(chapters, currentTime) {
        val idx = chapters.indexOfFirst { currentTime >= it.start && currentTime < it.end }
        if (idx >= 0) idx else chapters.lastIndex
    }
    val currentChapter = chapters.getOrNull(currentChapterIndex)

    val isChapterScope = prefs.progressScope == "chapter" && currentChapter != null
    val scopeStart = if (isChapterScope) currentChapter.start else 0.0
    val scopeEnd = if (isChapterScope) currentChapter.end else duration
    val scopeDuration = max(1.0, scopeEnd - scopeStart)
    val scopeElapsed = (currentTime - scopeStart).coerceIn(0.0, scopeDuration)
    val scopeRemaining = max(0.0, scopeDuration - scopeElapsed)

    var sliderDragValue by remember(activeItem.id, isChapterScope, currentChapterIndex) {
        mutableDoubleStateOf(-1.0)
    }
    val displayedElapsed = if (sliderDragValue >= 0.0) sliderDragValue else scopeElapsed

    val chapterTickFractions = remember(chapters, duration) {
        if (duration <= 0.0) emptyList()
        else chapters.map { (it.start / duration).toFloat().coerceIn(0f, 1f) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.ink,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar (matching FullPlayer.svelte: Minimize | Sound + Queue + Bookmark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ExpandMore, contentDescription = strings.playerClose)
                }

                Text(
                    text = strings.appName.uppercase(),
                    style = KoalaTheme.typography.label,
                    color = colors.muted,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        activePanel = if (activePanel == PlayerPanel.SOUND) PlayerPanel.NONE else PlayerPanel.SOUND
                    }) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = strings.playerSound,
                            tint = if (activePanel == PlayerPanel.SOUND) colors.accent else colors.ink,
                        )
                    }
                    IconButton(onClick = {
                        activePanel = if (activePanel == PlayerPanel.QUEUE) PlayerPanel.NONE else PlayerPanel.QUEUE
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = strings.playerQueue,
                            tint = if (activePanel == PlayerPanel.QUEUE || queueItems.isNotEmpty()) colors.accent else colors.ink,
                        )
                    }
                    IconButton(onClick = {
                        bookmarkNoteInput = currentChapter?.title ?: ""
                        showBookmarkDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = strings.playerAddBookmark,
                            tint = colors.accent,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = remoteHandoff != null) {
                remoteHandoff?.let { handoff ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(colors.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "⚡ Synced with another device (${formatClock(handoff.time)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                        )
                        IconButton(
                            onClick = { playerController.dismissRemoteHandoff() },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = colors.accent)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Interactive Turntable Deck
            val totalProgress = if (duration > 0.0) {
                (currentTime / duration).toFloat().coerceIn(0f, 1f)
            } else 0f

            TurntableDeck(
                title = activeItem.title,
                author = activeItem.author,
                coverUrl = coverUrlProvider(activeItem),
                playing = playing,
                marks = chapterTickFractions,
                speed = rate,
                progress = totalProgress,
                interactive = true,
                onScratchDeltaSeconds = { dtSec ->
                    playerController.skip(dtSec.toDouble())
                },
                modifier = Modifier.size(268.dp),
            )

            Spacer(Modifier.height(14.dp))

            // Title & Author & Chapter
            Text(
                text = activeItem.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    onDismiss()
                    onOpenBookDetail(activeItem.id)
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    activeItem.author.takeIf { it.isNotBlank() },
                    activeItem.seriesLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (currentChapter != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.card,
                    modifier = Modifier.clickable {
                        activePanel = if (activePanel == PlayerPanel.CHAPTERS) PlayerPanel.NONE else PlayerPanel.CHAPTERS
                    },
                ) {
                    Text(
                        text = "${ strings.playerChapterOf(currentChapterIndex + 1, chapters.size) } · ${currentChapter.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Big Clock Readout + Scope Toggle + Ends-At
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val effectiveRate = if (prefs.speedAdjusted) rate.toDouble() else 1.0
                val mainClockValue = if (prefs.remainingTime) {
                    "-${formatClock(max(0.0, scopeDuration - displayedElapsed) / effectiveRate)}"
                } else {
                    formatClock(displayedElapsed)
                }
                Text(
                    text = mainClockValue,
                    style = KoalaTheme.typography.time.copy(fontFamily = FontFamily.Monospace),
                    color = colors.ink,
                    modifier = Modifier.clickable {
                        prefsRepository.update { it.copy(remainingTime = !it.remainingTime) }
                    },
                )

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.sel,
                    modifier = Modifier.clickable {
                        val nextScope = if (prefs.progressScope == "chapter") "book" else "chapter"
                        prefsRepository.update { it.copy(progressScope = nextScope) }
                    },
                ) {
                    Text(
                        text = if (isChapterScope) strings.playerScopeChapter else strings.playerScopeBook,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.selInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            val remainingRealSeconds = scopeRemaining / rate.toDouble()
            val endsAtClock = formatEndsAtClock(System.currentTimeMillis(), remainingRealSeconds)
            val remFormatted = formatDuration(remainingRealSeconds, strings)
            Text(
                text = "${if (isChapterScope) strings.playerRemainingChapter(remFormatted) else strings.playerRemainingBook(remFormatted)} · ${strings.playerEndsAt(endsAtClock)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )

            // Jump-back undo & Sleep timer status pills
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lastJump?.let { target ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.card,
                        modifier = Modifier.clickable { playerController.undoJump() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.accent)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${strings.playerJumpedFrom(formatClock(target))} · ${strings.playerJumpBack}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                            )
                        }
                    }
                }

                if (sleep !is SleepTimerMode.Off) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.sel,
                        modifier = Modifier.clickable { activePanel = PlayerPanel.SLEEP },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.NightlightRound, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.selInk)
                            Spacer(Modifier.width(4.dp))
                            val label = when (sleep) {
                                SleepTimerMode.EndOfChapter -> strings.playerTimerEndOfChapter
                                is SleepTimerMode.SmartWindow -> "${strings.playerTimerSmart} (${formatClock(sleepRemaining)})"
                                is SleepTimerMode.Minutes -> formatClock(sleepRemaining)
                                SleepTimerMode.Off -> ""
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.selInk,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Timeline Slider with Chapter & Bookmark Markers
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = (displayedElapsed / scopeDuration).toFloat().coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            sliderDragValue = fraction * scopeDuration
                        },
                        onValueChangeFinished = {
                            if (sliderDragValue >= 0.0) {
                                playerController.jump(
                                    timeSeconds = scopeStart + sliderDragValue,
                                    jumpedLabelFormat = { from -> strings.playerJumpedFrom(formatClock(from)) },
                                    undoLabel = strings.playerJumpBack,
                                )
                                sliderDragValue = -1.0
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.track,
                        ),
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 10.dp),
                    ) {
                        if (!isChapterScope && duration > 0.0) {
                            chapters.forEach { ch ->
                                val x = (ch.start / duration).toFloat().coerceIn(0f, 1f) * size.width
                                drawLine(
                                    color = colors.muted.copy(alpha = 0.55f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.5f,
                                )
                            }
                        }
                        bookmarks.forEach { bm ->
                            if (bm.time in scopeStart..scopeEnd) {
                                val x = ((bm.time - scopeStart) / scopeDuration).toFloat().coerceIn(0f, 1f) * size.width
                                drawCircle(
                                    color = colors.accent,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(x, size.height / 2f),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatClock(displayedElapsed),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.muted,
                    )
                    Text(
                        text = formatClock(scopeDuration),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.muted,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Primary Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { playerController.prevChapter() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = strings.playerPrevChapter)
                }

                OutlinedButton(
                    onClick = { playerController.back() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("-${prefs.skipBack}s", fontFamily = FontFamily.Monospace)
                }

                Surface(
                    shape = CircleShape,
                    color = colors.accent,
                    contentColor = colors.onAccent,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(70.dp),
                    onClick = { playerController.toggle() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.75f, animationSpec = tween(150))) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.75f, animationSpec = tween(120)))
                            },
                            label = "full_play_pause",
                        ) { isPlayingNow ->
                            Icon(
                                imageVector = if (isPlayingNow) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingNow) strings.playerPause else strings.playerPlay,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { playerController.forward() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("+${prefs.skipForward}s", fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = { playerController.nextChapter() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = strings.playerNextChapter)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Secondary Feature Pill Bar
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = activePanel == PlayerPanel.SPEED,
                    onClick = { activePanel = if (activePanel == PlayerPanel.SPEED) PlayerPanel.NONE else PlayerPanel.SPEED },
                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(formatSpeed(rate, strings.isGerman)) },
                )
                FilterChip(
                    selected = activePanel == PlayerPanel.SLEEP || sleep !is SleepTimerMode.Off,
                    onClick = { activePanel = if (activePanel == PlayerPanel.SLEEP) PlayerPanel.NONE else PlayerPanel.SLEEP },
                    leadingIcon = { Icon(Icons.Default.NightlightRound, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.playerTimer) },
                )
                FilterChip(
                    selected = activePanel == PlayerPanel.SOUND,
                    onClick = { activePanel = if (activePanel == PlayerPanel.SOUND) PlayerPanel.NONE else PlayerPanel.SOUND },
                    leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.playerSound) },
                )
                FilterChip(
                    selected = activePanel == PlayerPanel.CHAPTERS,
                    onClick = { activePanel = if (activePanel == PlayerPanel.CHAPTERS) PlayerPanel.NONE else PlayerPanel.CHAPTERS },
                    leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("${strings.playerChapters} (${chapters.size})") },
                )
                FilterChip(
                    selected = activePanel == PlayerPanel.BOOKMARKS,
                    onClick = { activePanel = if (activePanel == PlayerPanel.BOOKMARKS) PlayerPanel.NONE else PlayerPanel.BOOKMARKS },
                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("${strings.playerBookmarks} (${bookmarks.size})") },
                )
                FilterChip(
                    selected = activePanel == PlayerPanel.QUEUE,
                    onClick = { activePanel = if (activePanel == PlayerPanel.QUEUE) PlayerPanel.NONE else PlayerPanel.QUEUE },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("${strings.playerQueue} (${queueItems.size})") },
                )
            }

            Spacer(Modifier.height(12.dp))

            // Expandable Drawer Panel
            AnimatedVisibility(
                visible = activePanel != PlayerPanel.NONE,
                enter = expandVertically(tween(240)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(220))
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    AnimatedContent(
                        targetState = activePanel,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "player_panel_transition",
                    ) { panel ->
                        when (panel) {
                            PlayerPanel.SPEED -> SpeedPanel(
                                currentRate = rate,
                                isGerman = strings.isGerman,
                                onRateChange = { playerController.setRate(it) },
                            )
                            PlayerPanel.SLEEP -> SleepTimerPanel(
                                playerController = playerController,
                                prefsRepository = prefsRepository,
                                prefs = prefs,
                                sleep = sleep,
                                onOpenBedtime = { activePanel = PlayerPanel.BEDTIME },
                            )
                            PlayerPanel.SOUND -> SoundEnginePanel(
                                prefs = prefs,
                                silenceSavedSeconds = silenceSaved,
                                onUpdatePrefs = { transform ->
                                    prefsRepository.update(transform)
                                    playerController.refreshSoundSettings()
                                },
                            )
                            PlayerPanel.CHAPTERS -> ChaptersPanel(
                                chapters = chapters,
                                currentChapterIndex = currentChapterIndex,
                                currentTime = currentTime,
                                playbackRate = rate,
                                onSeekTo = { target ->
                                    playerController.jump(
                                        timeSeconds = target,
                                        jumpedLabelFormat = { from -> strings.playerJumpedFrom(formatClock(from)) },
                                        undoLabel = strings.playerJumpBack,
                                    )
                                },
                            )
                            PlayerPanel.BOOKMARKS -> BookmarksPanel(
                                bookmarks = bookmarks,
                                onAddClick = {
                                    bookmarkNoteInput = currentChapter?.title ?: ""
                                    showBookmarkDialog = true
                                },
                                onJumpTo = { bm ->
                                    playerController.jump(
                                        timeSeconds = bm.time,
                                        jumpedLabelFormat = { from -> strings.playerJumpedFrom(formatClock(from)) },
                                        undoLabel = strings.playerJumpBack,
                                    )
                                },
                                onDelete = { bm ->
                                    scope.launch { playerController.deleteBookmark(bm.time) }
                                },
                            )
                            PlayerPanel.QUEUE -> QueuePanel(
                                entries = queueItems,
                                onPlayBook = { id -> playerController.play(id) },
                                onMove = { id, delta -> queueRepository.move(id, delta) },
                                onRemove = { id -> queueRepository.remove(id) },
                                onClear = { queueRepository.clear() },
                            )
                            PlayerPanel.BEDTIME -> {
                                BedtimeBlindGestureOverlay(
                                    playerController = playerController,
                                    playing = playing,
                                    sleepRemainingSeconds = sleepRemaining,
                                    onDismiss = { activePanel = PlayerPanel.NONE },
                                )
                            }
                            PlayerPanel.NONE -> {}
                        }
                    }
                }
            }
        }
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("${strings.playerAddBookmark} (${formatClock(currentTime)})") },
            text = {
                OutlinedTextField(
                    value = bookmarkNoteInput,
                    onValueChange = { bookmarkNoteInput = it },
                    label = { Text(strings.playerBookmarkNotePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val note = bookmarkNoteInput.trim().ifEmpty { formatClock(currentTime) }
                    scope.launch { playerController.addBookmark(note, currentTime) }
                    showBookmarkDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedPanel(
    currentRate: Float,
    isGerman: Boolean,
    onRateChange: (Float) -> Unit,
) {
    val strings = currentKoalaStrings
    val presets = listOf(0.75f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.playerSpeed, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatSpeed(currentRate, isGerman),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = KoalaTheme.colors.accent,
            )
        }

        Slider(
            value = currentRate,
            onValueChange = { onRateChange(clampRate(it)) },
            valueRange = 0.5f..3.5f,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = abs(currentRate - preset) < 0.03f,
                    onClick = { onRateChange(preset) },
                    label = { Text(formatSpeed(preset, isGerman)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerPanel(
    playerController: KoalaPlayerController,
    prefsRepository: PrefsRepository,
    prefs: KoalaPrefs,
    sleep: SleepTimerMode,
    onOpenBedtime: () -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.playerTimer, style = MaterialTheme.typography.titleMedium)
            if (sleep !is SleepTimerMode.Off) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { playerController.extendSleep(5) }) { Text("+5m") }
                    TextButton(onClick = { playerController.extendSleep(15) }) { Text("+15m") }
                    TextButton(onClick = { playerController.extendSleep(30) }) { Text("+30m") }
                    TextButton(onClick = { playerController.setSleep(SleepTimerMode.Off) }) {
                        Text(strings.playerTimerOff)
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(10, 15, 30, 45, 60, 90).forEach { mins ->
                FilterChip(
                    selected = (sleep as? SleepTimerMode.Minutes)?.minutes == mins,
                    onClick = { playerController.setSleepMinutes(mins) },
                    label = { Text("$mins ${strings.unitMinutes}") },
                )
            }
            FilterChip(
                selected = sleep is SleepTimerMode.EndOfChapter,
                onClick = { playerController.setSleep(SleepTimerMode.EndOfChapter) },
                label = { Text(strings.playerTimerEndOfChapter) },
            )
            FilterChip(
                selected = sleep is SleepTimerMode.SmartWindow,
                onClick = {
                    val (lo, hi) = clampSleepWindow(prefs.sleepMin, prefs.sleepMax, changedMin = false)
                    playerController.setSleepSmartWindow(lo, hi)
                },
                label = { Text("${strings.playerTimerSmart} (${prefs.sleepMin}–${prefs.sleepMax}m)") },
            )
        }

        HorizontalDivider(color = colors.line)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.settingsSleepFade, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = prefs.sleepFade,
                onCheckedChange = { enabled -> prefsRepository.update { it.copy(sleepFade = enabled) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.settingsAutoSleep, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = prefs.autoSleep,
                onCheckedChange = { enabled -> prefsRepository.update { it.copy(autoSleep = enabled) } },
            )
        }

        Button(
            onClick = onOpenBedtime,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E1408),
                contentColor = Color(0xFFD49B43),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.NightlightRound, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.bedtimeTitle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundEnginePanel(
    prefs: KoalaPrefs,
    silenceSavedSeconds: Double,
    onUpdatePrefs: ((KoalaPrefs) -> KoalaPrefs) -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.soundTitle, style = MaterialTheme.typography.titleMedium)

        // Sound Profile
        Text(strings.soundProfileLabel, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "off" to strings.soundProfileOff,
                "voice" to strings.soundProfileVoice,
                "night" to strings.soundProfileNight,
                "commute" to strings.soundProfileCommute,
                "warmth" to strings.soundProfileWarmth,
            ).forEach { (id, label) ->
                FilterChip(
                    selected = prefs.soundProfile == id,
                    onClick = { onUpdatePrefs { it.copy(soundProfile = id) } },
                    label = { Text(label) },
                )
            }
        }

        // Volume Boost slider (1.0x .. 2.0x)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(strings.soundVolumeBoost, style = MaterialTheme.typography.bodyMedium)
            Text("${(prefs.boost * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
        Slider(
            value = prefs.boost,
            onValueChange = { b -> onUpdatePrefs { it.copy(boost = b) } },
            valueRange = 1.0f..2.0f,
        )

        // Voice Boost toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.soundVoiceBoost, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = prefs.voiceBoost,
                onCheckedChange = { v -> onUpdatePrefs { it.copy(voiceBoost = v) } },
            )
        }

        // Skip Silence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.soundSkipSilence, style = MaterialTheme.typography.bodyMedium)
                if (silenceSavedSeconds > 1.0) {
                    Text(
                        text = formatDuration(silenceSavedSeconds, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
            }
            Switch(
                checked = prefs.skipSilence,
                onCheckedChange = { v -> onUpdatePrefs { it.copy(skipSilence = v) } },
            )
        }

        // Needle Drop Cue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.soundNeedleDrop, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = prefs.needleDrop,
                onCheckedChange = { v -> onUpdatePrefs { it.copy(needleDrop = v) } },
            )
        }

        // Vinyl Crackle Ambience
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.soundVinylCrackle, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = prefs.vinylCrackle,
                onCheckedChange = { v -> onUpdatePrefs { it.copy(vinylCrackle = v) } },
            )
        }
    }
}

@Composable
private fun ChaptersPanel(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    currentTime: Double,
    playbackRate: Float,
    onSeekTo: (Double) -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    var goToTimeInput by remember { mutableStateOf("") }
    var chapterFilterQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (chapters.size > 12) {
            OutlinedTextField(
                value = chapterFilterQuery,
                onValueChange = { chapterFilterQuery = it },
                placeholder = { Text(strings.filterChapters) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = goToTimeInput,
                onValueChange = { goToTimeInput = it },
                placeholder = { Text(strings.playerGoToTime) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    parseClock(goToTimeInput)?.let { sec ->
                        onSeekTo(sec)
                        goToTimeInput = ""
                    }
                },
            ) {
                Text("Go")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chapters.forEachIndexed { idx, ch ->
                if (chapterFilterQuery.isNotBlank() && !ch.title.contains(chapterFilterQuery.trim(), ignoreCase = true)) {
                    return@forEachIndexed
                }
                val isCurrent = idx == currentChapterIndex
                val chDuration = max(0.0, ch.end - ch.start)
                val chEndsAt = if (isCurrent) {
                    val rem = max(0.0, ch.end - currentTime) / playbackRate.toDouble()
                    " · ${strings.playerEndsAt(formatEndsAtClock(System.currentTimeMillis(), rem))}"
                } else ""

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrent) colors.curBg else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(ch.start) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${idx + 1}. ${ch.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrent) colors.curInk else colors.ink,
                            )
                            Text(
                                text = "${formatClock(ch.start)} (${formatDuration(chDuration, strings)})$chEndsAt",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksPanel(
    bookmarks: List<Bookmark>,
    onAddClick: () -> Unit,
    onJumpTo: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.playerBookmarks, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.playerAddBookmark)
            }
        }

        bookmarks.sortedBy { it.time }.forEach { bm ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onJumpTo(bm) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bm.title.ifBlank { formatClock(bm.time) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = formatClock(bm.time),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.accent,
                    )
                }
                IconButton(onClick = { onDelete(bm) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    entries: List<LibraryItem>,
    onPlayBook: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.playerQueue, style = MaterialTheme.typography.titleMedium)
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(strings.clearFilters)
                }
            }
        }

        entries.forEachIndexed { idx, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPlayBook(entry.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${idx + 1}. ${entry.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { onMove(entry.id, -1) },
                    enabled = idx > 0,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = { onMove(entry.id, +1) },
                    enabled = idx < entries.lastIndex,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(
                    onClick = { onRemove(entry.id) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Bedtime blind-gesture view:
 * Absolute zero ocular glare (pitch black background with ultra-dim amber accents).
 * Allows listeners to control playback completely blind by touch:
 * - Single tap anywhere: Play / Pause
 * - Double tap: +15 Min sleep timer
 * - Swipe left: Skip back 30s
 * - Swipe right: Skip forward 30s
 * - Exit via top right X
 */
@Composable
fun BedtimeBlindGestureOverlay(
    playerController: KoalaPlayerController,
    playing: Boolean,
    sleepRemainingSeconds: Double,
    onDismiss: () -> Unit,
) {
    val strings = currentKoalaStrings
    val koalaHaptics = LocalKoalaHaptics.current

    val amberColor = androidx.compose.ui.graphics.Color(0xFFD49B43)
    val dimAmberColor = androidx.compose.ui.graphics.Color(0xFF7A551E)
    val darkAmberBg = androidx.compose.ui.graphics.Color(0xFF1E1408)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .pointerInput(playing) {
                    var dragAccum = 0f
                    val stepPx = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                            if (abs(dragAccum) >= stepPx) {
                                if (dragAccum > 0) {
                                    playerController.forward()
                                    koalaHaptics.chapterSnap()
                                } else {
                                    playerController.back()
                                    koalaHaptics.chapterSnap()
                                }
                                dragAccum = 0f
                            }
                        },
                    )
                }
                .pointerInput(playing) {
                    detectTapGestures(
                        onTap = {
                            if (playing) {
                                playerController.pause()
                            } else {
                                playerController.play()
                            }
                            koalaHaptics.grooveTick()
                        },
                        onDoubleTap = {
                            playerController.extendSleep(15)
                            koalaHaptics.sleepArmed()
                        },
                    )
                },
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.NightlightRound,
                        contentDescription = null,
                        tint = dimAmberColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = strings.bedtimeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = dimAmberColor,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.bedtimeExit,
                        tint = dimAmberColor,
                    )
                }
            }

            // Center: Big Analog Status Indicator
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .border(2.dp, dimAmberColor, CircleShape)
                        .background(darkAmberBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = amberColor,
                        modifier = Modifier.size(56.dp),
                    )
                }

                if (sleepRemainingSeconds > 0) {
                    val mins = (sleepRemainingSeconds / 60).toInt()
                    val secs = (sleepRemainingSeconds % 60).toInt()
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.headlineLarge,
                        color = amberColor,
                    )
                } else {
                    Text(
                        text = strings.playerTimerOff,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dimAmberColor,
                    )
                }

                OutlinedButton(
                    onClick = {
                        playerController.extendSleep(15)
                        koalaHaptics.sleepArmed()
                    },
                    border = BorderStroke(1.dp, dimAmberColor),
                ) {
                    Text(strings.bedtimeAdd15, color = amberColor)
                }
            }

            // Bottom blind gesture guide
            Text(
                text = strings.bedtimeHint,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFF553B18),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                    .align(Alignment.BottomCenter),
            )
        }
    }
}
