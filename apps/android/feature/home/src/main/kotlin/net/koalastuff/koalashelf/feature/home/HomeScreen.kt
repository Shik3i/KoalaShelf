package net.koalastuff.koalashelf.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.util.formatClock
import net.koalastuff.koalashelf.core.data.util.formatDuration
import net.koalastuff.koalashelf.core.data.util.formatRelativeTime
import net.koalastuff.koalashelf.core.data.util.inSeriesOrder
import net.koalastuff.koalashelf.core.designsystem.AuthorPortrait
import net.koalastuff.koalashelf.core.designsystem.BookCover
import net.koalastuff.koalashelf.core.designsystem.BookCrateShelf
import net.koalastuff.koalashelf.core.designsystem.CrateRecordUi
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.RecordCrate
import net.koalastuff.koalashelf.core.designsystem.TurntableDeck
import net.koalastuff.koalashelf.core.designsystem.VinylDisc
import net.koalastuff.koalashelf.core.network.Library
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.MediaProgress
import net.koalastuff.koalashelf.core.network.Shelf
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    username: String,
    libraries: List<Library>,
    activeLibraryId: String,
    shelves: List<Shelf>,
    allBooks: List<LibraryItem>,
    progressMap: Map<String, MediaProgress>,
    todayListeningSeconds: Double,
    prefs: KoalaPrefs,
    isPlayingItemId: String?,
    isPlaying: Boolean,
    coverUrlProvider: (LibraryItem) -> String?,
    onSelectLibrary: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onHideFromContinue: (String) -> Unit = {},
    onSetDailyGoalMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    var showCrateDigger by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var dismissedHeroIds by remember { mutableStateOf(setOf<String>()) }

    val greeting = remember(strings) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 11 -> strings.greetingMorning
            hour < 18 -> strings.greetingDay
            else -> strings.greetingEvening
        }
    }

    val activeLibraryName = remember(libraries, activeLibraryId, strings) {
        libraries.firstOrNull { it.id == activeLibraryId }?.name ?: strings.appName
    }

    val continueListeningBooks = remember(shelves, allBooks, progressMap, dismissedHeroIds) {
        val shelfBooks = shelves.filterIsInstance<Shelf.BookShelf>()
            .firstOrNull { it.id.contains("continue", ignoreCase = true) }
            ?.books
            .orEmpty()
        val raw = if (shelfBooks.isNotEmpty()) {
            shelfBooks
        } else {
            allBooks.filter { book ->
                val p = progressMap[book.id] ?: book.userMediaProgress
                p != null && !p.isFinished && !p.hideFromContinueListening && p.progress > 0.01
            }.sortedByDescending { book ->
                (progressMap[book.id] ?: book.userMediaProgress)?.lastUpdate ?: 0L
            }
        }
        raw.filterNot { it.id in dismissedHeroIds }
    }

    val heroBook = continueListeningBooks.firstOrNull()

    val orderedVisibleShelves = remember(shelves, prefs.hiddenShelves, prefs.shelfOrder) {
        val visible = shelves.filterNot { it.id in prefs.hiddenShelves }
        if (prefs.shelfOrder.isEmpty()) {
            visible
        } else {
            visible.sortedBy { s ->
                val idx = prefs.shelfOrder.indexOf(s.id)
                if (idx >= 0) idx else 999
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header + Greeting + GoalRing matching `apps/web/src/routes/+page.svelte`
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeLibraryName.uppercase(),
                        style = KoalaTheme.typography.label,
                        color = colors.muted,
                    )
                    Text(
                        text = if (username.isNotBlank()) "$greeting, $username" else greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                    )
                }

                if (prefs.dailyGoal > 0) {
                    GoalRingBadge(
                        todaySeconds = todayListeningSeconds,
                        goalMinutes = prefs.dailyGoal,
                        onClick = { showGoalEditor = true },
                    )
                }
            }
        }

        // Hero "Continue Listening" Card with TurntableDeck (`+page.svelte`)
        if (heroBook != null) {
            item(key = "hero_${heroBook.id}") {
                val prog = progressMap[heroBook.id] ?: heroBook.userMediaProgress
                val fraction = (prog?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
                val currentSec = prog?.currentTime ?: (heroBook.durationSeconds * fraction)
                val remainingSec = max(0.0, heroBook.durationSeconds - currentSec)
                val isHeroPlaying = isPlaying && isPlayingItemId == heroBook.id
                val lastUpdateMs = prog?.lastUpdate ?: 0L

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.card,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                        .clickable { onOpenBook(heroBook.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TurntableDeck(
                            title = heroBook.title,
                            author = heroBook.author,
                            coverUrl = coverUrlProvider(heroBook),
                            playing = isHeroPlaying,
                            progress = fraction,
                            interactive = false,
                            modifier = Modifier.size(124.dp),
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strings.homeContinue.uppercase(),
                                    style = KoalaTheme.typography.label,
                                    color = colors.accent,
                                )
                                IconButton(
                                    onClick = {
                                        dismissedHeroIds = dismissedHeroIds + heroBook.id
                                        onHideFromContinue(prog?.id ?: heroBook.id)
                                    },
                                    modifier = Modifier.size(22.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = colors.muted,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }

                            Text(
                                text = heroBook.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    heroBook.author.takeIf { it.isNotBlank() },
                                    heroBook.narrator.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = colors.accent,
                                trackColor = colors.track,
                            )

                            val relTime = if (lastUpdateMs > 0L) " · ${formatRelativeTime(lastUpdateMs, strings.isGerman)}" else ""
                            Text(
                                text = "${(fraction * 100).roundToInt()}% · ${strings.playerRemainingBook(formatDuration(remainingSec, strings))}$relTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Button(
                                onClick = { onPlayBook(heroBook.id) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
                                ),
                            ) {
                                AnimatedContent(
                                    targetState = isHeroPlaying,
                                    transitionSpec = {
                                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                                    },
                                    label = "hero_play_button",
                                ) { playingHero ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (playingHero) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        val btnText = if (playingHero) {
                                            strings.playerPause
                                        } else {
                                            strings.bookResumeAt(formatClock(currentSec))
                                        }
                                        Text(btnText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Personalized Shelves (`Crate.svelte` for book shelves, `RecordCrate.svelte` for series shelves)
        itemsIndexed(orderedVisibleShelves, key = { idx, shelf -> "${shelf.id}_$idx" }) { _, shelf ->
            val shelfTitle = remember(shelf.id, shelf.label, strings) {
                when {
                    shelf.id.contains("continue-series", ignoreCase = true) -> strings.homeContinueSeries
                    shelf.id.contains("continue", ignoreCase = true) -> strings.homeContinue
                    shelf.id.contains("recently-added", ignoreCase = true) -> strings.homeRecentlyAdded
                    shelf.id.contains("recent-series", ignoreCase = true) -> strings.homeRecentSeries
                    shelf.id.contains("discover", ignoreCase = true) -> strings.homeDiscover
                    shelf.id.contains("listen-again", ignoreCase = true) -> strings.homeListenAgain
                    shelf.id.contains("newest-authors", ignoreCase = true) -> strings.homeNewestAuthors
                    else -> shelf.label
                }
            }

            when (shelf) {
                is Shelf.SeriesShelf -> {
                    if (shelf.series.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = shelfTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                itemsIndexed(shelf.series, key = { idx, series -> "${series.id}_$idx" }) { _, series ->
                                    val ordered = inSeriesOrder(series.books, series.name)
                                    val records = ordered.map { b ->
                                        val p = progressMap[b.id] ?: b.userMediaProgress
                                        CrateRecordUi(
                                            id = b.id,
                                            title = b.title,
                                            author = b.author,
                                            coverUrl = coverUrlProvider(b),
                                            isFinished = p?.isFinished == true,
                                        )
                                    }
                                    RecordCrate(
                                        name = series.name,
                                        records = records,
                                        note = strings.booksCount(series.books.size),
                                        onRecordClick = onOpenBook,
                                        onSeriesClick = { onOpenSeries(series.id, series.name) },
                                        modifier = Modifier.width(220.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                is Shelf.BookShelf -> {
                    val booksToRender = if (shelf.id.contains("continue", ignoreCase = true) && heroBook != null) {
                        shelf.books.filterNot { it.id == heroBook.id || it.id in dismissedHeroIds }
                    } else {
                        shelf.books
                    }
                    if (booksToRender.isNotEmpty()) {
                        val records = booksToRender.map { b ->
                            val p = progressMap[b.id] ?: b.userMediaProgress
                            CrateRecordUi(
                                id = b.id,
                                title = b.title,
                                author = b.author,
                                coverUrl = coverUrlProvider(b),
                                isFinished = p?.isFinished == true,
                            )
                        }
                        val noteText = if (shelf.id.contains("recently-added", ignoreCase = true)) {
                            if (strings.isGerman) "Diese Woche" else "This week"
                        } else {
                            strings.booksCount(booksToRender.size)
                        }
                        BookCrateShelf(
                            title = shelfTitle,
                            note = noteText,
                            records = records,
                            onRecordClick = onOpenBook,
                        )
                    }
                }

                is Shelf.AuthorsShelf -> {
                    if (shelf.authors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = shelfTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                itemsIndexed(shelf.authors, key = { idx, author -> "${author.id.ifBlank { author.name }}_$idx" }) { _, author ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .width(104.dp)
                                            .clickable { onOpenAuthor(author.id, author.name) },
                                    ) {
                                        AuthorPortrait(
                                            name = author.name,
                                            size = 76.dp,
                                        )
                                        Text(
                                            text = author.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = strings.booksCount(author.numBooks ?: 0),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCrateDigger) {
        CrateDiggerDialog(
            allBooks = allBooks,
            progressMap = progressMap,
            coverUrlProvider = coverUrlProvider,
            onDismiss = { showCrateDigger = false },
            onOpenBook = { id ->
                showCrateDigger = false
                onOpenBook(id)
            },
            onPlayBook = { id ->
                showCrateDigger = false
                onPlayBook(id)
            },
        )
    }

    if (showGoalEditor) {
        DailyGoalDialog(
            todaySeconds = todayListeningSeconds,
            currentGoalMinutes = prefs.dailyGoal,
            onSelectGoal = {
                onSetDailyGoalMinutes(it)
                showGoalEditor = false
            },
            onOpenStats = {
                showGoalEditor = false
                onOpenStats()
            },
            onDismiss = { showGoalEditor = false },
        )
    }
}

@Composable
fun ShelfBookCard(
    book: LibraryItem,
    progress: MediaProgress?,
    coverUrl: String?,
    spinEnabled: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val progFraction = (progress?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
    val isFinished = progress?.isFinished == true

    Column(
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (spinEnabled && isPlaying) {
                VinylDisc(
                    title = book.title,
                    author = book.author,
                    coverUrl = coverUrl,
                    playing = true,
                    gold = isFinished,
                    modifier = Modifier.size(142.dp),
                )
            } else {
                BookCover(
                    title = book.title,
                    author = book.author,
                    coverUrl = coverUrl,
                    progress = progFraction,
                    isFinished = isFinished,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 14.dp,
                )
            }

            Surface(
                shape = CircleShape,
                color = colors.accent.copy(alpha = 0.92f),
                contentColor = colors.onAccent,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp),
                onClick = onPlay,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GoalRingBadge(
    todaySeconds: Double,
    goalMinutes: Int,
    onClick: () -> Unit,
) {
    val colors = KoalaTheme.colors
    val todayMin = (todaySeconds / 60.0).roundToInt()
    val rawProgress = if (goalMinutes > 0) (todaySeconds / (goalMinutes * 60.0)).toFloat().coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 500),
        label = "goal_ring_progress",
    )
    val reached = rawProgress >= 1f

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (reached) colors.sel else colors.card,
        modifier = Modifier
            .border(1.dp, colors.line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = colors.track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (reached) Color(0xFFD4A640) else colors.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = "${todayMin}/${goalMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGoalDialog(
    todaySeconds: Double,
    currentGoalMinutes: Int,
    onSelectGoal: (Int) -> Unit,
    onOpenStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = currentKoalaStrings
    val todayMin = (todaySeconds / 60.0).roundToInt()
    val presets = listOf(0, 15, 20, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.dailyGoalLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${strings.statsToday}: $todayMin / $currentGoalMinutes ${strings.unitMinutes}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = currentGoalMinutes == mins,
                            onClick = { onSelectGoal(mins) },
                            label = { Text(if (mins == 0) strings.playerTimerOff else "$mins ${strings.unitMinutes}") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenStats) {
                Text(strings.navStats)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.playerClose)
            }
        },
    )
}

@Composable
private fun CrateDiggerDialog(
    allBooks: List<LibraryItem>,
    progressMap: Map<String, MediaProgress>,
    coverUrlProvider: (LibraryItem) -> String?,
    onDismiss: () -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    var filterMode by remember { mutableIntStateOf(1) } // 0 = all, 1 = unread, 2 = short (<6h)
    var spinSeed by remember { mutableIntStateOf(0) }

    val pool = remember(allBooks, progressMap, filterMode) {
        allBooks.filter { book ->
            val p = progressMap[book.id] ?: book.userMediaProgress
            when (filterMode) {
                1 -> p == null || (!p.isFinished && p.progress < 0.05)
                2 -> book.durationSeconds in 60.0..(6 * 3600.0) && p?.isFinished != true
                else -> true
            }
        }.ifEmpty { allBooks }
    }

    val picked = remember(pool, spinSeed) {
        if (pool.isEmpty()) null else pool[Random(System.nanoTime() + spinSeed).nextInt(pool.size)]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.homeCrateDigger)
                Text(
                    text = strings.homeCrateDiggerSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0; spinSeed++ },
                        label = { Text(strings.filterAll) },
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1; spinSeed++ },
                        label = { Text(strings.filterNotStarted) },
                    )
                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2; spinSeed++ },
                        label = { Text("< 6 ${strings.unitHours}") },
                    )
                }

                if (picked != null) {
                    VinylDisc(
                        title = picked.title,
                        author = picked.author,
                        coverUrl = coverUrlProvider(picked),
                        playing = true,
                        modifier = Modifier.size(164.dp),
                    )
                    Text(
                        text = picked.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${picked.author} · ${formatDuration(picked.durationSeconds, strings)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    OutlinedButton(onClick = { spinSeed++ }) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.sortRandom)
                    }
                }
            }
        },
        confirmButton = {
            if (picked != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpenBook(picked.id) }) {
                        Text(strings.bookDetails)
                    }
                    Button(onClick = { onPlayBook(picked.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.bookPlay)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.playerClose)
            }
        },
    )
}
