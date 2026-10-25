package net.koalastuff.koalashelf.feature.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.repository.DownloadJobState
import net.koalastuff.koalashelf.core.data.repository.DownloadedBook
import net.koalastuff.koalashelf.core.data.util.formatBytes
import net.koalastuff.koalashelf.core.data.util.formatClock
import net.koalastuff.koalashelf.core.data.util.formatDuration
import net.koalastuff.koalashelf.core.data.util.formatEndsAtClock
import net.koalastuff.koalashelf.core.data.util.htmlToText
import net.koalastuff.koalashelf.core.designsystem.BookCover
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.LinerNotesSleeve
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Collection
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.ListeningSession
import net.koalastuff.koalashelf.core.network.MediaProgress
import net.koalastuff.koalashelf.core.network.Playlist
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    book: LibraryItem,
    progress: MediaProgress?,
    downloadedBook: DownloadedBook?,
    downloadJob: DownloadJobState?,
    bookmarks: List<Bookmark>,
    moreByAuthor: List<LibraryItem>,
    bookSessions: List<ListeningSession>,
    collections: List<Collection>,
    playlists: List<Playlist>,
    prefs: KoalaPrefs,
    isPlayingThisBook: Boolean,
    coverUrlProvider: (LibraryItem) -> String?,
    onBack: () -> Unit,
    onPlayBook: (Double?) -> Unit,
    onToggleQueue: () -> Unit,
    isQueued: Boolean,
    onMarkFinished: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onAddBookmark: (Double, String) -> Unit,
    onDeleteBookmark: (Double) -> Unit,
    onAddToCollection: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    onOpenOtherBook: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    canDownload: Boolean = true,
    onDownloadZip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }

    val progFraction = (progress?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
    val isFinished = progress?.isFinished == true
    val currentTime = progress?.currentTime ?: 0.0
    val remainingSec = max(0.0, book.durationSeconds - currentTime)
    val cleanDescription = remember(book.media.metadata.description) {
        htmlToText(book.media.metadata.description)
    }
    val chapterMarks = remember(book.media.chapters, book.durationSeconds) {
        if (book.durationSeconds <= 0.0) emptyList()
        else book.media.chapters.map { (it.start / book.durationSeconds).toFloat().coerceIn(0f, 1f) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        }

        // 3D Flip LinerNotesSleeve
        item {
            LinerNotesSleeve(
                title = book.title,
                author = book.author,
                narrator = book.narrator,
                coverUrl = coverUrlProvider(book),
                catalogCode = book.id.take(8).uppercase(),
                stereoLabel = strings.bookStereoHifi,
                linerNotesLabel = strings.bookLinerNotes,
                flipFrontLabel = strings.bookFlipFront,
                synopsis = cleanDescription,
                playing = isPlayingThisBook,
                gold = isFinished,
                marks = chapterMarks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        // Title, Author, Series, Primary Play & Progress
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                )
                book.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.muted,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (book.author.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val firstAuthor = book.media.metadata.authors.firstOrNull()
                                if (firstAuthor != null) {
                                    onOpenAuthor(firstAuthor.id, firstAuthor.name)
                                } else {
                                    onSearch(book.author)
                                }
                            },
                            label = { Text("${strings.bookBy} ${book.author}") },
                        )
                    }
                    val narrators = book.media.metadata.narrators.filter { it.isNotBlank() }
                    if (narrators.isNotEmpty()) {
                        narrators.forEach { narr ->
                            FilterChip(
                                selected = false,
                                onClick = { onSearch(narr) },
                                label = { Text("${strings.bookReadBy} $narr") },
                            )
                        }
                    } else if (book.narrator.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { onSearch(book.narrator) },
                            label = { Text("${strings.bookReadBy} ${book.narrator}") },
                        )
                    }
                    val firstSeries = book.media.metadata.parsedSeriesList.firstOrNull()
                    if (firstSeries != null && firstSeries.name.isNotBlank()) {
                        val seqLabel = firstSeries.sequence?.takeIf { it.isNotBlank() }?.let { " #$it" } ?: ""
                        FilterChip(
                            selected = true,
                            onClick = { onOpenSeries(firstSeries.id, firstSeries.name) },
                            label = { Text("${firstSeries.name}$seqLabel") },
                        )
                    }
                }

                // Primary Play / Resume Button
                Button(
                    onClick = { onPlayBook(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    val label = when {
                        isFinished -> strings.bookListenAgain
                        progFraction > 0.01f -> strings.bookResumeAt(formatClock(currentTime))
                        else -> strings.bookPlay
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }

                if (progFraction > 0.0f || isFinished) {
                    LinearProgressIndicator(
                        progress = { if (isFinished) 1f else progFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = colors.accent,
                        trackColor = colors.track,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(progFraction * 100).roundToInt()}% · ${strings.playerRemainingBook(formatDuration(remainingSec, strings))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                        Text(
                            text = strings.playerEndsAt(formatEndsAtClock(System.currentTimeMillis(), remainingSec / prefs.defaultRate.toDouble())),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                // Secondary Action Buttons Row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isDownloaded = downloadedBook != null
                    val isDownloading = downloadJob?.status == DownloadJobState.Status.RUNNING
                    OutlinedButton(
                        onClick = {
                            if (isDownloaded || isDownloading) onRemoveDownload()
                            else onStartDownload()
                        },
                    ) {
                        when {
                            isDownloaded -> {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.removeDownload)
                            }
                            isDownloading -> {
                                CircularProgressIndicator(
                                    progress = { (downloadJob?.progressPercent ?: 0) / 100f },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.downloadingProgress(downloadJob?.progressPercent ?: 0))
                            }
                            else -> {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.downloadAction)
                            }
                        }
                    }

                    OutlinedButton(onClick = onToggleQueue) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isQueued) strings.bookInQueue else strings.bookAddToQueue)
                    }

                    OutlinedButton(
                        onClick = { onMarkFinished(!isFinished) },
                    ) {
                        Icon(
                            imageVector = if (isFinished) Icons.Default.Restore else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFinished) strings.bookMarkUnfinished else strings.bookMarkFinished)
                    }

                    if (progFraction > 0.01f && !isFinished) {
                        OutlinedButton(onClick = onResetProgress) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bookResetProgress)
                        }
                    }

                    OutlinedButton(onClick = { showCollectionPicker = true }) {
                        Icon(Icons.Default.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.bookAddToCollection)
                    }

                    OutlinedButton(onClick = { showPlaylistPicker = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.bookAddToPlaylist)
                    }

                    if (canDownload && onDownloadZip != null) {
                        OutlinedButton(onClick = onDownloadZip) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (book.isFile) strings.saveFile else strings.saveZip)
                        }
                    }
                }
            }
        }

        // Tabs: Chapters | Bookmarks | History | Files | More by Author | Details
        item {
            val filesLabel = if (strings.isGerman) "Dateien" else "Files"
            val tabTitles = listOf(
                "${strings.bookChapters} (${book.media.chapters.size})",
                "${strings.bookBookmarks} (${bookmarks.size})",
                strings.bookHistory,
                "$filesLabel (${book.media.audioFiles.size})",
                strings.bookMoreByAuthor,
                strings.bookDetails,
            )
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.bg,
                contentColor = colors.ink,
                edgePadding = 20.dp,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
        }

        // Tab Body
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (selectedTab) {
                    0 -> {
                        book.media.chapters.forEachIndexed { idx, ch ->
                            val isCurrent = currentTime >= ch.start && currentTime < ch.end
                            val chDur = max(0.0, ch.end - ch.start)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) colors.curBg else colors.card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                    .clickable { onPlayBook(ch.start) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
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
                                            text = "${formatClock(ch.start)} · ${formatDuration(chDur, strings)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = strings.bookPlay,
                                        tint = colors.accent,
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        OutlinedButton(onClick = { showAddBookmarkDialog = true }) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${strings.playerAddBookmark} (${formatClock(currentTime)})")
                        }
                        bookmarks.sortedBy { it.time }.forEach { bm ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayBook(bm.time) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.title.ifBlank { formatClock(bm.time) }, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = formatClock(bm.time),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = colors.accent,
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.time) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.muted)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // History Tab with SessionRow span visualization & start/end jump pills (`SessionRow.svelte`)
                        bookSessions.forEach { sess ->
                            val dur = max(1.0, if (sess.duration > 0.0) sess.duration else book.durationSeconds)
                            val loFrac = (kotlin.math.min(sess.startTime, sess.currentTime) / dur).toFloat().coerceIn(0f, 1f)
                            val hiFrac = (max(sess.startTime, sess.currentTime) / dur).toFloat().coerceIn(0f, 1f)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = sess.date.ifBlank { formatClock(sess.currentTime) },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = "+${formatDuration(sess.timeListening, strings)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.accent,
                                        )
                                    }
                                    // Span bar
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .border(0.5.dp, colors.line, RoundedCornerShape(999.dp)),
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { max(0.02f, hiFrac - loFrac) },
                                            modifier = Modifier.fillMaxSize(),
                                            color = colors.accent,
                                            trackColor = colors.track,
                                        )
                                    }
                                    // Jump pills: startTime -> currentTime
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { onPlayBook(sess.startTime) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            label = { Text(formatClock(sess.startTime), fontFamily = FontFamily.Monospace) },
                                        )
                                        Text("→", color = colors.muted)
                                        FilterChip(
                                            selected = false,
                                            onClick = { onPlayBook(sess.currentTime) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            label = { Text(formatClock(sess.currentTime), fontFamily = FontFamily.Monospace) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // Audio Files Tab (`book.media.audioFiles` in `routes/book/[id]/+page.svelte`)
                        book.media.audioFiles.forEachIndexed { idx, file ->
                            val fname = file.metadata?.filename?.ifBlank { null } ?: "Track ${idx + 1}"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}. $fname",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val codecInfo = listOfNotNull(
                                            file.codec?.uppercase()?.takeIf { it.isNotBlank() },
                                            file.bitRate?.takeIf { it > 0 }?.let { "${(it / 1000)} kbps" },
                                        ).joinToString(" · ")
                                        if (codecInfo.isNotEmpty()) {
                                            Text(
                                                text = codecInfo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.muted,
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${formatDuration(file.duration, strings)} · ${formatBytes(file.metadata?.size ?: 0L, strings.isGerman)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                    }

                    4 -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(moreByAuthor, key = { it.id }) { other ->
                                Column(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable { onOpenOtherBook(other.id) },
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    BookCover(
                                        title = other.title,
                                        author = other.author,
                                        coverUrl = coverUrlProvider(other),
                                        modifier = Modifier.size(136.dp),
                                        cornerRadius = 12.dp,
                                    )
                                    Text(
                                        text = other.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    5 -> {
                        if (cleanDescription.isNotBlank()) {
                            Text(
                                text = cleanDescription,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.ink,
                            )
                        }
                        val meta = book.media.metadata
                        val firstAudio = book.media.audioFiles.firstOrNull()
                        val formatSpec = listOfNotNull(
                            firstAudio?.metadata?.ext?.removePrefix(".")?.uppercase()?.takeIf { it.isNotBlank() },
                            firstAudio?.codec?.uppercase()?.takeIf { it.isNotBlank() },
                            firstAudio?.bitRate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
                        ).joinToString(" · ")
                        if (formatSpec.isNotEmpty()) {
                            MetadataRow(strings.bookFormat, formatSpec)
                        }
                        MetadataRow(strings.bookSize, formatBytes(book.totalBytes, strings.isGerman))
                        meta.publisher?.takeIf { it.isNotBlank() }?.let {
                            MetadataRow(strings.bookPublisher, it)
                        }
                        meta.publishedYear?.takeIf { it.isNotBlank() }?.let {
                            MetadataRow(strings.bookPublished, it)
                        }
                        meta.language?.takeIf { it.isNotBlank() }?.let {
                            MetadataRow(strings.bookLanguage, it)
                        }
                        meta.isbn?.takeIf { it.isNotBlank() }?.let {
                            MetadataRow("ISBN", it)
                        }
                        meta.asin?.takeIf { it.isNotBlank() }?.let {
                            MetadataRow("ASIN", it)
                        }
                        if (meta.genres.isNotEmpty()) {
                            Text(strings.filterGenre, style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                meta.genres.forEach { g ->
                                    FilterChip(selected = false, onClick = { onSearch(g) }, label = { Text(g) })
                                }
                            }
                        }
                        if (book.media.tags.isNotEmpty()) {
                            Text(strings.filterTag, style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                book.media.tags.forEach { t ->
                                    FilterChip(selected = false, onClick = { onSearch(t) }, label = { Text(t) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCollectionPicker) {
        AlertDialog(
            onDismissRequest = { showCollectionPicker = false },
            title = { Text(strings.bookAddToCollection) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collections.forEach { col ->
                        TextButton(
                            onClick = {
                                onAddToCollection(col.id)
                                showCollectionPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(col.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionPicker = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text(strings.bookAddToPlaylist) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                onAddToPlaylist(pl.id)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(pl.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }

    if (showAddBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text(strings.playerAddBookmark) },
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
                    onAddBookmark(currentTime, note)
                    bookmarkNoteInput = ""
                    showAddBookmarkDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    val colors = KoalaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
