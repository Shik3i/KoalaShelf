package net.koalastuff.koalashelf.feature.library

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.koalastuff.koalashelf.core.data.i18n.AppLocale
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.repository.ChapterSearchHit
import net.koalastuff.koalashelf.core.data.repository.DownloadJobState
import net.koalastuff.koalashelf.core.data.repository.DownloadedBook
import net.koalastuff.koalashelf.core.data.repository.StorageRadarInfo
import net.koalastuff.koalashelf.core.data.util.clampRate
import net.koalastuff.koalashelf.core.data.util.describeUserAgent
import net.koalastuff.koalashelf.core.data.util.formatBytes
import net.koalastuff.koalashelf.core.data.util.formatClock
import net.koalastuff.koalashelf.core.data.util.formatDuration
import net.koalastuff.koalashelf.core.data.util.formatLocalDay
import net.koalastuff.koalashelf.core.data.util.formatSpeed
import net.koalastuff.koalashelf.core.data.util.inSeriesOrder
import net.koalastuff.koalashelf.core.data.util.sequenceIn
import net.koalastuff.koalashelf.core.designsystem.AuthorPortrait
import net.koalastuff.koalashelf.core.designsystem.BookCover
import net.koalastuff.koalashelf.core.designsystem.BookCrateShelf
import net.koalastuff.koalashelf.core.designsystem.CoverMosaic
import net.koalastuff.koalashelf.core.designsystem.CrateRecordUi
import net.koalastuff.koalashelf.core.designsystem.KoalaAccentPreset
import net.koalastuff.koalashelf.core.designsystem.KoalaFontChoice
import net.koalastuff.koalashelf.core.designsystem.KoalaTextSize
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.KoalaThemeMode
import net.koalastuff.koalashelf.core.designsystem.MosaicCoverItem
import net.koalastuff.koalashelf.core.designsystem.RecordCrate
import net.koalastuff.koalashelf.core.network.AuthSession
import net.koalastuff.koalashelf.core.network.Author
import net.koalastuff.koalashelf.core.network.AuthorDetail
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Collection
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.LibraryStats
import net.koalastuff.koalashelf.core.network.ListeningSession
import net.koalastuff.koalashelf.core.network.ListeningStats
import net.koalastuff.koalashelf.core.network.MediaProgress
import net.koalastuff.koalashelf.core.network.Narrator
import net.koalastuff.koalashelf.core.network.Playlist
import net.koalastuff.koalashelf.core.network.SearchResult
import net.koalastuff.koalashelf.core.network.Series
import net.koalastuff.koalashelf.core.network.ServerUser
import net.koalastuff.koalashelf.core.network.YearStats
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================
// 1. SERIES CRATE SCREEN & SERIES DETAIL SCREEN
// ============================================================================

@Composable
fun SeriesScreen(
    seriesList: List<Series>,
    progressMap: Map<String, MediaProgress>,
    coverUrlProvider: (LibraryItem) -> String?,
    onOpenSeries: (String, String) -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    var query by remember { mutableStateOf("") }

    val filtered = remember(seriesList, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) seriesList
        else seriesList.filter { it.name.lowercase().contains(q) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${strings.navSeries} (${filtered.size})",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.textFilterPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.id }) { series ->
                val ordered = remember(series.books, series.name) {
                    inSeriesOrder(series.books, series.name)
                }
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
                )
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    seriesName: String,
    books: List<LibraryItem>,
    progressMap: Map<String, MediaProgress>,
    coverUrlProvider: (LibraryItem) -> String?,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
    onDownloadAll: (List<LibraryItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    val orderedBooks = remember(books, seriesName) {
        inSeriesOrder(books, seriesName)
    }
    val finishedCount = orderedBooks.count { (progressMap[it.id] ?: it.userMediaProgress)?.isFinished == true }
    val nextBookToPlay = orderedBooks.firstOrNull { (progressMap[it.id] ?: it.userMediaProgress)?.isFinished != true }
        ?: orderedBooks.firstOrNull()
    val totalDuration = orderedBooks.sumOf { it.durationSeconds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(seriesName, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "$finishedCount / ${orderedBooks.size} · ${formatDuration(totalDuration, strings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nextBookToPlay != null) {
                        Button(onClick = { onPlayBook(nextBookToPlay.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bookPlay)
                        }
                    }
                    OutlinedButton(onClick = { onDownloadAll(orderedBooks) }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.downloadAction)
                    }
                }
            }
        }

        items(orderedBooks, key = { it.id }) { book ->
            val seq = sequenceIn(book, seriesName)
            val prog = progressMap[book.id] ?: book.userMediaProgress
            val isFinished = prog?.isFinished == true
            val fraction = (prog?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .clickable { onOpenBook(book.id) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(
                        title = book.title,
                        author = book.author,
                        coverUrl = coverUrlProvider(book),
                        progress = fraction,
                        isFinished = isFinished,
                        modifier = Modifier.size(68.dp),
                        cornerRadius = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val seqText = seq?.let { if (it % 1.0 == 0.0) "#${it.toInt()}" else "#$it" }
                        Text(
                            text = if (seqText != null) "$seqText · ${book.title}" else book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${book.author} · ${formatDuration(book.durationSeconds, strings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    IconButton(onClick = { onPlayBook(book.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. AUTHORS & NARRATORS SCREEN + AUTHOR DETAIL SCREEN
// ============================================================================

@Composable
fun AuthorsScreen(
    authors: List<Author>,
    narrators: List<Narrator>,
    onOpenAuthor: (String, String) -> Unit,
    onSelectNarratorBooks: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.navAuthors, style = MaterialTheme.typography.headlineMedium)
            TabRow(selectedTabIndex = selectedTab, containerColor = colors.bg) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${strings.filterAuthor} (${authors.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${strings.filterNarrator} (${narrators.size})") },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.textFilterPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (selectedTab == 0) {
            val filteredAuthors = remember(authors, query) {
                val q = query.trim().lowercase()
                if (q.isEmpty()) authors else authors.filter { it.name.lowercase().contains(q) }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredAuthors, key = { it.id }) { author ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .clickable { onOpenAuthor(author.id, author.name) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthorPortrait(
                                name = author.name,
                                size = 48.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = author.name,
                                    style = MaterialTheme.typography.titleMedium,
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
        } else {
            val filteredNarrators = remember(narrators, query) {
                val q = query.trim().lowercase()
                if (q.isEmpty()) narrators else narrators.filter { it.name.lowercase().contains(q) }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredNarrators, key = { it.name }) { narrator ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .clickable { onSelectNarratorBooks(narrator.name) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthorPortrait(
                                name = narrator.name,
                                size = 48.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = narrator.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = strings.booksCount(narrator.numBooks),
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

@Composable
fun AuthorDetailScreen(
    authorName: String,
    authorDetail: AuthorDetail?,
    books: List<LibraryItem>,
    coverUrlProvider: (LibraryItem) -> String?,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    AuthorPortrait(
                        name = authorName,
                        size = 64.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(authorName, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = strings.booksCount(books.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }
                authorDetail?.description?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        items(books, key = { it.id }) { book ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .clickable { onOpenBook(book.id) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(
                        title = book.title,
                        author = book.author,
                        coverUrl = coverUrlProvider(book),
                        modifier = Modifier.size(64.dp),
                        cornerRadius = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = listOfNotNull(
                                book.seriesLabel.takeIf { it.isNotBlank() },
                                formatDuration(book.durationSeconds, strings),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    IconButton(onClick = { onPlayBook(book.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. COLLECTIONS & PLAYLISTS SCREENS
// ============================================================================

@Composable
fun CollectionsAndPlaylistsScreen(
    isPlaylistsMode: Boolean,
    collections: List<Collection>,
    playlists: List<Playlist>,
    coverUrlProvider: (LibraryItem) -> String?,
    onCreate: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onRemoveBook: (String, String) -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    var showCreateDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPlaylistsMode) strings.navPlaylists else strings.navCollections,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+")
                }
            }
        }

        if (!isPlaylistsMode) {
            items(collections, key = { it.id }) { col ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (col.books.isNotEmpty()) {
                        BookCrateShelf(
                            title = col.name,
                            note = strings.booksCount(col.books.size),
                            records = col.books.map { b ->
                                CrateRecordUi(
                                    id = b.id,
                                    title = b.title,
                                    author = b.author,
                                    coverUrl = coverUrlProvider(b),
                                )
                            },
                            onRecordClick = onOpenBook,
                        )
                    }
                    CollectionOrPlaylistCard(
                        name = col.name,
                        description = col.description,
                        books = col.books,
                        showMosaic = false,
                        coverUrlProvider = coverUrlProvider,
                        onDelete = { onDelete(col.id) },
                        onRemoveBook = { bookId -> onRemoveBook(col.id, bookId) },
                        onOpenBook = onOpenBook,
                        onPlayBook = onPlayBook,
                    )
                }
            }
        } else {
            items(playlists, key = { it.id }) { pl ->
                CollectionOrPlaylistCard(
                    name = pl.name,
                    description = pl.description,
                    books = pl.items.mapNotNull { it.libraryItem },
                    showMosaic = true,
                    coverUrlProvider = coverUrlProvider,
                    onDelete = { onDelete(pl.id) },
                    onRemoveBook = { bookId -> onRemoveBook(pl.id, bookId) },
                    onOpenBook = onOpenBook,
                    onPlayBook = onPlayBook,
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isPlaylistsMode) strings.navPlaylists else strings.navCollections) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(strings.sortTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text(strings.bookDescription) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameInput.isNotBlank()) {
                        onCreate(nameInput.trim(), descInput.trim().takeIf { it.isNotEmpty() })
                        nameInput = ""
                        descInput = ""
                        showCreateDialog = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }
}

@Composable
private fun CollectionOrPlaylistCard(
    name: String,
    description: String?,
    books: List<LibraryItem>,
    showMosaic: Boolean = false,
    coverUrlProvider: (LibraryItem) -> String?,
    onDelete: () -> Unit,
    onRemoveBook: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
) {
    val colors = KoalaTheme.colors
    val strings = currentKoalaStrings

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMosaic) {
                    CoverMosaic(
                        items = books.map { b ->
                            MosaicCoverItem(
                                id = b.id,
                                title = b.title,
                                author = b.author,
                                coverUrl = coverUrlProvider(b),
                            )
                        },
                        modifier = Modifier.size(60.dp),
                        cornerRadius = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Text(
                        text = strings.booksCount(books.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = colors.muted)
                }
            }

            books.forEach { book ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenBook(book.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(
                        title = book.title,
                        author = book.author,
                        coverUrl = coverUrlProvider(book),
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 8.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.author, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    IconButton(onClick = { onPlayBook(book.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                    }
                    IconButton(onClick = { onRemoveBook(book.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. SEARCH SCREEN (Server Search + All Libraries Scope + Chapter Search)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    searchAllLibraries: Boolean,
    onToggleScope: (Boolean) -> Unit,
    searchResult: SearchResult,
    chapterHits: List<ChapterSearchHit>,
    coverUrlProvider: (LibraryItem) -> String?,
    onOpenBook: (String) -> Unit,
    onOpenAuthor: (String, String) -> Unit = { _, _ -> },
    onOpenSeries: (String, String) -> Unit = { _, _ -> },
    onFilterLibraryByNarrator: (String) -> Unit = {},
    onFilterLibraryByGenre: (String) -> Unit = {},
    onFilterLibraryByTag: (String) -> Unit = {},
    onPlayBookAt: (LibraryItem, Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.navSearch, style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(strings.textFilterPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !searchAllLibraries,
                        onClick = { onToggleScope(false) },
                        label = { Text(strings.navLibrary) },
                    )
                    FilterChip(
                        selected = searchAllLibraries,
                        onClick = { onToggleScope(true) },
                        label = { Text(strings.filterAll) },
                    )
                }
            }
        }

        if (searchResult.book.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.navLibrary} (${searchResult.book.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(searchResult.book, key = { it.libraryItem.id }) { hit ->
                val book = hit.libraryItem
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBook(book.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(
                            title = book.title,
                            author = book.author,
                            coverUrl = coverUrlProvider(book),
                            modifier = Modifier.size(56.dp),
                            cornerRadius = 8.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(book.author, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        IconButton(onClick = { onPlayBookAt(book, null) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                        }
                    }
                }
            }
        }

        if (searchResult.narrators.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.filterNarrator} (${searchResult.narrators.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    searchResult.narrators.forEach { narr ->
                        val countLabel = if (narr.numBooks > 0) " (${narr.numBooks})" else ""
                        FilterChip(
                            selected = false,
                            onClick = { onFilterLibraryByNarrator(narr.name) },
                            label = { Text("${narr.name}$countLabel") },
                        )
                    }
                }
            }
        }

        if (searchResult.authors.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.navAuthors} (${searchResult.authors.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    searchResult.authors.forEach { author ->
                        FilterChip(
                            selected = false,
                            onClick = { onOpenAuthor(author.id, author.name) },
                            label = { Text(author.name) },
                        )
                    }
                }
            }
        }

        if (searchResult.series.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.navSeries} (${searchResult.series.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    searchResult.series.forEach { sHit ->
                        FilterChip(
                            selected = false,
                            onClick = { onOpenSeries(sHit.series.id, sHit.series.name) },
                            label = { Text(sHit.series.name) },
                        )
                    }
                }
            }
        }

        if (searchResult.genres.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.filterGenre} (${searchResult.genres.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    searchResult.genres.forEach { genre ->
                        val countLabel = if (genre.numItems > 0) " (${genre.numItems})" else ""
                        FilterChip(
                            selected = false,
                            onClick = { onFilterLibraryByGenre(genre.name) },
                            label = { Text("${genre.name}$countLabel") },
                        )
                    }
                }
            }
        }

        if (searchResult.tags.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.filterTag} (${searchResult.tags.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    searchResult.tags.forEach { tag ->
                        val countLabel = if (tag.numItems > 0) " (${tag.numItems})" else ""
                        FilterChip(
                            selected = false,
                            onClick = { onFilterLibraryByTag(tag.name) },
                            label = { Text("${tag.name}$countLabel") },
                        )
                    }
                }
            }
        }

        if (chapterHits.isNotEmpty()) {
            item {
                Text(
                    text = "${strings.bookChapters} (${chapterHits.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(chapterHits, key = { "${it.book.id}_${it.startSeconds}" }) { match ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayBookAt(match.book, match.startSeconds) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(match.chapterTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${match.book.title} · ${formatClock(match.startSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. BOOKMARKS HUB SCREEN (with Markdown & CSV Export)
// ============================================================================

@Composable
fun BookmarksScreen(
    bookmarksWithBooks: List<Pair<Bookmark, LibraryItem?>>,
    markdownExportProvider: () -> String,
    csvExportProvider: () -> String,
    onJumpToBookmark: (String, Double) -> Unit,
    onDeleteBookmark: (String, Double) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    val clipboard = LocalClipboardManager.current

    val grouped = remember(bookmarksWithBooks) {
        bookmarksWithBooks.groupBy { it.first.libraryItemId }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.navBookmarks, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(markdownExportProvider()))
                        onShowToast("Markdown copied")
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MD")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(csvExportProvider()))
                        onShowToast("CSV copied")
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CSV")
                    }
                }
            }
        }

        items(grouped.entries.toList(), key = { it.key }) { (itemId, entries) ->
            val book = entries.firstOrNull()?.second
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = book?.title ?: itemId,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    entries.forEach { (bm, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onJumpToBookmark(itemId, bm.time) }
                                .padding(vertical = 4.dp),
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
                            IconButton(onClick = { onDeleteBookmark(itemId, bm.time) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. DOWNLOADS & STORAGE RADAR SCREEN
// ============================================================================

@Composable
fun DownloadsScreen(
    downloadedBooks: List<DownloadedBook>,
    downloadJobs: Map<String, DownloadJobState>,
    storageRadar: StorageRadarInfo,
    progressMap: Map<String, MediaProgress>,
    onPlayOfflineBook: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onCleanFinishedDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    val finishedDownloadsCount = downloadedBooks.count { dl ->
        progressMap[dl.id]?.isFinished == true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(strings.downloadsTitle, style = MaterialTheme.typography.headlineMedium)
        }

        // Storage Radar Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.storageRadar, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${formatBytes(storageRadar.audiobooksBytes, strings.isGerman)} / ${formatBytes(storageRadar.freeBytes, strings.isGerman)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    val totalCap = max(1L, storageRadar.totalBytes)
                    val usedFraction = (storageRadar.audiobooksBytes.toDouble() / totalCap.toDouble()).toFloat().coerceIn(0.01f, 1f)
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = colors.accent,
                        trackColor = colors.track,
                    )
                    if (finishedDownloadsCount > 0) {
                        OutlinedButton(onClick = onCleanFinishedDownloads) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.cleanFinishedDownloads(finishedDownloadsCount))
                        }
                    }
                }
            }
        }

        if (downloadedBooks.isEmpty() && downloadJobs.isEmpty()) {
            item {
                Text(strings.downloadsEmpty, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        } else {
            items(downloadedBooks, key = { it.id }) { dl ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dl.item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${dl.item.author} · ${formatBytes(dl.sizeBytes, strings.isGerman)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        IconButton(onClick = { onPlayOfflineBook(dl.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = strings.bookPlay, tint = colors.accent)
                        }
                        IconButton(onClick = { onDeleteDownload(dl.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.removeDownload, tint = colors.muted)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. STATS, YEAR IN REVIEW & LISTENING HISTORY SCREEN
// ============================================================================

@Composable
fun StatsAndHistoryScreen(
    stats: ListeningStats,
    yearInReview: YearStats?,
    selectedYear: Int,
    finishedBooksCount: Int,
    sessions: List<ListeningSession>,
    libraryStats: LibraryStats? = null,
    historyCsvExportProvider: () -> String = { "" },
    users: List<ServerUser> = emptyList(),
    selectedUserId: String? = null,
    onSelectUser: (String?) -> Unit = {},
    isAdmin: Boolean = false,
    onSelectYear: (Int) -> Unit,
    onOpenBook: (String) -> Unit,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Stats, 1 = History

    val activeDaysCount = remember(stats.days) {
        stats.days.count { it.value >= 60.0 }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.statsTitle, style = MaterialTheme.typography.headlineMedium)
                    if (selectedTab == 1 && sessions.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(historyCsvExportProvider()))
                            onShowToast(strings.exportCsv)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.exportCsv)
                        }
                    }
                }
                if (isAdmin && users.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.statsAdminUserSelect,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                            if (selectedUserId != null) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.accent.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = strings.statsAdminBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            item(key = "self") {
                                FilterChip(
                                    selected = selectedUserId == null,
                                    onClick = { onSelectUser(null) },
                                    label = { Text(strings.statsViewingSelf) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accent.copy(alpha = 0.2f),
                                        selectedLabelColor = colors.ink,
                                    ),
                                )
                            }
                            items(users, key = { it.id }) { u ->
                                FilterChip(
                                    selected = selectedUserId == u.id,
                                    onClick = { onSelectUser(u.id) },
                                    label = { Text(u.username) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accent.copy(alpha = 0.2f),
                                        selectedLabelColor = colors.ink,
                                    ),
                                )
                            }
                        }
                    }
                }
                TabRow(selectedTabIndex = selectedTab, containerColor = colors.bg) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.navStats) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.navHistory) },
                    )
                }
            }
        }

        if (selectedTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatMetricCard(
                        label = strings.statsTotal,
                        value = formatDuration(stats.totalTime, strings),
                        modifier = Modifier.weight(1f),
                    )
                    StatMetricCard(
                        label = strings.statsDays,
                        value = "$activeDaysCount",
                        modifier = Modifier.weight(1f),
                    )
                    StatMetricCard(
                        label = strings.filterFinished,
                        value = "$finishedBooksCount",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (stats.dayOfWeek.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(strings.statsWeekdays, style = MaterialTheme.typography.titleMedium)
                            val maxVal = max(1.0, stats.dayOfWeek.values.maxOrNull() ?: 1.0)
                            stats.dayOfWeek.entries.forEach { (day, sec) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = day.take(3),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(36.dp),
                                    )
                                    LinearProgressIndicator(
                                        progress = { (sec / maxVal).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = colors.accent,
                                        trackColor = colors.track,
                                    )
                                    Text(
                                        text = formatDuration(sec, strings),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                        modifier = Modifier.width(64.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Year in Review Card (only for own account, as ABS year review endpoint is per-user)
            if (selectedUserId == null) {
                item {
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${strings.statsYearInReview} $selectedYear",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(currentYear - 1, currentYear).forEach { y ->
                                        FilterChip(
                                            selected = selectedYear == y,
                                            onClick = { onSelectYear(y) },
                                            label = { Text("$y") },
                                        )
                                    }
                                }
                            }
                            if (yearInReview != null) {
                                Text(
                                    text = "${strings.statsTotal}: ${formatDuration(yearInReview.totalListeningTime, strings)} · ${strings.filterFinished}: ${yearInReview.numBooksFinished}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
            }

            // Library Facts Card (apps/web/src/lib/components/LibraryFacts.svelte)
            if (libraryStats != null && libraryStats.totalItems > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(strings.libStatsTitle, style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatMetricCard(
                                    label = strings.navLibrary,
                                    value = "${libraryStats.totalItems}",
                                    modifier = Modifier.weight(1f),
                                )
                                StatMetricCard(
                                    label = strings.libStatsTotalDuration,
                                    value = formatDuration(libraryStats.totalDuration, strings),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatMetricCard(
                                    label = strings.libStatsAuthors,
                                    value = "${libraryStats.totalAuthors}",
                                    modifier = Modifier.weight(1f),
                                )
                                StatMetricCard(
                                    label = strings.libStatsTotalSize,
                                    value = formatBytes(libraryStats.totalSize, strings.isGerman),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            val topGenres = libraryStats.genresWithCount.sortedByDescending { it.count }.take(5)
                            if (topGenres.isNotEmpty()) {
                                Text(strings.libStatsGenres, style = MaterialTheme.typography.labelLarge, color = colors.muted)
                                val maxGenreCount = max(1, topGenres.first().count)
                                topGenres.forEach { g ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = g.genre,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(100.dp),
                                        )
                                        LinearProgressIndicator(
                                            progress = { (g.count.toFloat() / maxGenreCount.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp)),
                                            color = colors.accent,
                                            trackColor = colors.track,
                                        )
                                        Text(
                                            text = "${g.count}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = colors.muted,
                                        )
                                    }
                                }
                            }
                            val longestItems = libraryStats.longestItems.take(3)
                            if (longestItems.isNotEmpty()) {
                                Text(strings.libStatsLongest, style = MaterialTheme.typography.labelLarge, color = colors.muted)
                                longestItems.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { if (item.id.isNotBlank()) onOpenBook(item.id) }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = formatDuration(item.duration, strings),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = colors.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (sessions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                    ) {
                        Text(
                            text = if (selectedUserId != null) strings.statsUserEmpty else strings.historyEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                items(sessions, key = { it.id }) { sess ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .clickable {
                            if (sess.libraryItemId.isNotBlank()) onOpenBook(sess.libraryItemId)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sess.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${sess.displayAuthor} · ${sess.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        Text(
                            text = formatDuration(sess.timeListening, strings),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.card,
        modifier = modifier.border(1.dp, colors.line, RoundedCornerShape(14.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = colors.accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
        }
    }
}

// ============================================================================
// 8. SETTINGS & PERSONALIZATION SCREEN
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: KoalaPrefs,
    prefsRepository: PrefsRepository,
    activeAccountLabel: String,
    authSessions: List<AuthSession> = emptyList(),
    onOpenAccountSwitcher: () -> Unit,
    onSignOutDevice: (String) -> Unit = {},
    onChangePassword: (String, String) -> Unit = { _, _ -> },
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors
    val clipboard = LocalClipboardManager.current

    var customHexInput by remember(prefs.customAccent) { mutableStateOf(prefs.customAccent) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(strings.settingsTitle, style = MaterialTheme.typography.headlineMedium)
        }

        // Account & Server Section
        item {
            SettingsCard(title = strings.navAccounts) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(activeAccountLabel, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenAccountSwitcher) {
                        Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.navAccounts)
                    }
                }
            }
        }

        // Appearance Section
        item {
            SettingsCard(title = strings.settingsAppearance) {
                // Language
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLocale.entries.forEach { loc ->
                        FilterChip(
                            selected = prefs.locale == loc.tag,
                            onClick = { prefsRepository.update { it.copy(locale = loc.tag) } },
                            label = { Text(loc.label) },
                        )
                    }
                }

                // Theme Mode
                Text(strings.settingsTheme, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        KoalaThemeMode.SYSTEM.id to strings.settingsThemeSystem,
                        KoalaThemeMode.LIGHT.id to strings.settingsThemeLight,
                        KoalaThemeMode.DARK.id to strings.settingsThemeDark,
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.themeMode == id,
                            onClick = { prefsRepository.update { it.copy(themeMode = id) } },
                            label = { Text(label) },
                        )
                    }
                }

                // Accent Presets + Custom OKLCH Hex
                Text(strings.settingsAccent, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KoalaAccentPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = prefs.accent == preset.id,
                            onClick = { prefsRepository.update { it.copy(accent = preset.id) } },
                            label = { Text(preset.id.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }

                if (prefs.accent == KoalaAccentPreset.CUSTOM.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { customHexInput = it },
                            label = { Text(strings.settingsCustomColor) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { prefsRepository.update { it.copy(customAccent = customHexInput) } }) {
                            Text("OK")
                        }
                    }
                }

                // Spin Vinyl toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.settingsVinylSpin, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = prefs.spin,
                        onCheckedChange = { v -> prefsRepository.update { it.copy(spin = v) } },
                    )
                }

                // Haptic feedback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(strings.settingsHaptics, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.settingsHapticsHint, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Switch(
                        checked = prefs.haptics,
                        onCheckedChange = { v -> prefsRepository.update { it.copy(haptics = v) } },
                    )
                }

                // Font Choice
                Text(strings.settingsFont, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KoalaFontChoice.entries.forEach { fc ->
                        FilterChip(
                            selected = prefs.font == fc.id,
                            onClick = { prefsRepository.update { it.copy(font = fc.id) } },
                            label = { Text(fc.id.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }

                // Text Size
                Text(strings.settingsTextSize, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KoalaTextSize.entries.forEach { ts ->
                        FilterChip(
                            selected = prefs.textSize == ts.id,
                            onClick = { prefsRepository.update { it.copy(textSize = ts.id) } },
                            label = { Text(ts.id.uppercase()) },
                        )
                    }
                }

                // Cover Size (sm / md / lg)
                Text(strings.settingsCoverSize, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "sm" to strings.settingsCoverSm,
                        "md" to strings.settingsCoverMd,
                        "lg" to strings.settingsCoverLg,
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.coverSize == id,
                            onClick = { prefsRepository.update { it.copy(coverSize = id) } },
                            label = { Text(label) },
                        )
                    }
                }

                // Start Page (home / library)
                Text(strings.settingsStartPage, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "home" to strings.navHome,
                        "library" to strings.navLibrary,
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.startPage == id,
                            onClick = { prefsRepository.update { it.copy(startPage = id) } },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        // Playback Settings Section
        item {
            SettingsCard(title = strings.settingsPlayback) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.playerSpeed, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatSpeed(prefs.defaultRate, strings.isGerman),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.accent,
                    )
                }
                Slider(
                    value = prefs.defaultRate,
                    onValueChange = { r -> prefsRepository.update { it.copy(defaultRate = clampRate(r)) } },
                    valueRange = 0.5f..3.5f,
                )

                Text(strings.settingsSkipBack, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrefsRepository.SKIP_STEPS.forEach { sec ->
                        FilterChip(
                            selected = prefs.skipBack == sec,
                            onClick = { prefsRepository.update { it.copy(skipBack = sec) } },
                            label = { Text("${sec}s") },
                        )
                    }
                }

                Text(strings.settingsSkipForward, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrefsRepository.SKIP_STEPS.forEach { sec ->
                        FilterChip(
                            selected = prefs.skipForward == sec,
                            onClick = { prefsRepository.update { it.copy(skipForward = sec) } },
                            label = { Text("${sec}s") },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.settingsSmartRewind, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.settingsSmartRewindHint, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Switch(
                        checked = prefs.smartRewind,
                        onCheckedChange = { v -> prefsRepository.update { it.copy(smartRewind = v) } },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.settingsRememberRate, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = prefs.rememberRate,
                        onCheckedChange = { v -> prefsRepository.update { it.copy(rememberRate = v) } },
                    )
                }
            }
        }

        // Account Security Section (apps/web/src/lib/components/AccountSecurity.svelte)
        item {
            SettingsCard(title = strings.settingsSecurity) {
                OutlinedButton(onClick = { showPasswordDialog = true }) {
                    Text(strings.changePassword)
                }
                if (authSessions.isNotEmpty()) {
                    Text(strings.activeDevices, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    authSessions.forEach { sess ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buildString {
                                        append(describeUserAgent(sess.userAgent))
                                        if (sess.current) append(" (${strings.thisDevice})")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = listOfNotNull(
                                        sess.ipAddress?.takeIf { it.isNotBlank() },
                                        if (sess.updatedAt > 0L) formatLocalDay(sess.updatedAt) else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            if (!sess.current) {
                                TextButton(onClick = { onSignOutDevice(sess.id) }) {
                                    Text(strings.signOutDevice, color = colors.accent)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backup & About Section
        item {
            SettingsCard(title = strings.settingsAbout) {
                Text(strings.settingsAboutText, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        val json = prefsRepository.exportBackupJson()
                        clipboard.setText(AnnotatedString(json))
                        onShowToast(strings.settingsBackupExport)
                    }) {
                        Text(strings.settingsBackupExport)
                    }
                    OutlinedButton(onClick = { showImportDialog = true }) {
                        Text(strings.settingsBackupImport)
                    }
                    OutlinedButton(onClick = {
                        prefsRepository.reset()
                        onShowToast(strings.settingsReset)
                    }) {
                        Text(strings.settingsReset)
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(strings.changePassword) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPasswordInput,
                        onValueChange = { currentPasswordInput = it },
                        label = { Text(strings.currentPassword) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text(strings.newPassword) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPasswordInput.isNotBlank()) {
                        onChangePassword(currentPasswordInput, newPasswordInput)
                        currentPasswordInput = ""
                        newPasswordInput = ""
                        showPasswordDialog = false
                    }
                }) {
                    Text(strings.changePasswordSubmit)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(strings.settingsBackupImport) },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    placeholder = { Text("{ ... }") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (importJsonText.isNotBlank()) {
                        prefsRepository.importBackupJson(importJsonText)
                        importJsonText = ""
                        showImportDialog = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = KoalaTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
