package net.koalastuff.koalashelf.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.prefs.SavedLibraryView
import net.koalastuff.koalashelf.core.data.repository.DownloadedBook
import net.koalastuff.koalashelf.core.data.repository.DurationPreset
import net.koalastuff.koalashelf.core.data.repository.LibraryQueryState
import net.koalastuff.koalashelf.core.data.repository.LibrarySortOption
import net.koalastuff.koalashelf.core.data.repository.LibraryStatusFilter
import net.koalastuff.koalashelf.core.data.util.formatDuration
import net.koalastuff.koalashelf.core.designsystem.BookCover
import net.koalastuff.koalashelf.core.designsystem.BookShelfTile
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.VinylDisc
import net.koalastuff.koalashelf.core.network.FilterData
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.MediaProgress
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    books: List<LibraryItem>,
    queryState: LibraryQueryState,
    onQueryStateChange: (LibraryQueryState) -> Unit,
    filterData: FilterData,
    progressMap: Map<String, MediaProgress>,
    downloadedBooks: Map<String, DownloadedBook>,
    prefs: KoalaPrefs,
    isPlayingItemId: String?,
    isPlaying: Boolean,
    coverUrlProvider: (LibraryItem) -> String?,
    onOpenBook: (String) -> Unit,
    onPlayBook: (String) -> Unit,
    onSaveView: (SavedLibraryView) -> Unit,
    onDeleteSavedView: (String) -> Unit,
    onBatchQueue: (List<LibraryItem>) -> Unit,
    onBatchMarkFinished: (List<LibraryItem>, Boolean) -> Unit,
    onBatchDownload: (List<LibraryItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    var isGridView by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFacetDialog by remember { mutableStateOf(false) }
    var showSaveViewDialog by remember { mutableStateOf(false) }
    var saveViewName by remember { mutableStateOf("") }

    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Search + Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = strings.libraryTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = strings.libraryCount(books.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        multiSelectMode = !multiSelectMode
                        if (!multiSelectMode) selectedBookIds = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = if (multiSelectMode) colors.accent else colors.muted,
                        )
                    }
                    IconButton(onClick = { showFacetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = strings.filterAll,
                            tint = if (queryState.activeFilterCount > 0) colors.accent else colors.muted,
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = strings.sortLabel,
                                tint = colors.muted,
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            listOf(
                                LibrarySortOption.TITLE to strings.sortTitle,
                                LibrarySortOption.AUTHOR to strings.sortAuthor,
                                LibrarySortOption.ADDED to strings.sortAdded,
                                LibrarySortOption.PLAYED to strings.sortProgress,
                                LibrarySortOption.DURATION to strings.sortDuration,
                                LibrarySortOption.PUBLISHED to strings.sortPublished,
                                LibrarySortOption.RANDOM to strings.sortRandom,
                            ).forEach { (opt, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        val nextDesc = if (queryState.sort == opt) !queryState.desc else opt.defaultDesc
                                        onQueryStateChange(
                                            queryState.copy(
                                                sort = opt,
                                                desc = nextDesc,
                                                shuffleSeed = if (opt == LibrarySortOption.RANDOM) queryState.shuffleSeed + 1 else queryState.shuffleSeed,
                                            ),
                                        )
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (queryState.sort == opt) {
                                            Icon(
                                                imageVector = if (queryState.desc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) strings.viewList else strings.viewShelf,
                            tint = colors.muted,
                        )
                    }
                }
            }

            // Search bar with natural duration support (< 6h, 5-10h, etc.)
            OutlinedTextField(
                value = queryState.textQuery,
                onValueChange = { onQueryStateChange(queryState.copy(textQuery = it)) },
                placeholder = { Text(strings.textFilterPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (queryState.textQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryStateChange(queryState.copy(textQuery = "")) }) {
                            Icon(Icons.Default.Close, contentDescription = strings.clearFilters)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Status filter chips + collapse series + saved views
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    LibraryStatusFilter.ALL to strings.filterAll,
                    LibraryStatusFilter.IN_PROGRESS to strings.filterInProgress,
                    LibraryStatusFilter.NOT_STARTED to strings.filterNotStarted,
                    LibraryStatusFilter.FINISHED to strings.filterFinished,
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = queryState.status == filter,
                        onClick = { onQueryStateChange(queryState.copy(status = filter)) },
                        label = { Text(label) },
                    )
                }

                FilterChip(
                    selected = queryState.groupSeries,
                    onClick = { onQueryStateChange(queryState.copy(groupSeries = !queryState.groupSeries)) },
                    label = { Text(strings.groupSeries) },
                )

                prefs.savedViews.forEach { sv ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onQueryStateChange(queryState.copy(textQuery = sv.query))
                        },
                        label = { Text(sv.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDeleteSavedView(sv.id) },
                            )
                        },
                    )
                }

                IconButton(
                    onClick = { showSaveViewDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Multi-select batch action bar
            AnimatedVisibility(
                visible = multiSelectMode,
                enter = expandVertically(tween(220)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.sel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${selectedBookIds.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.selInk,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val selectedItems = books.filter { it.id in selectedBookIds }
                            IconButton(
                                onClick = {
                                    onBatchQueue(selectedItems)
                                    multiSelectMode = false
                                    selectedBookIds = emptySet()
                                },
                                enabled = selectedItems.isNotEmpty(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = strings.bookAddToQueue)
                            }
                            IconButton(
                                onClick = {
                                    onBatchMarkFinished(selectedItems, true)
                                    multiSelectMode = false
                                    selectedBookIds = emptySet()
                                },
                                enabled = selectedItems.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = strings.bookMarkFinished)
                            }
                            IconButton(
                                onClick = {
                                    onBatchMarkFinished(selectedItems, false)
                                    multiSelectMode = false
                                    selectedBookIds = emptySet()
                                },
                                enabled = selectedItems.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = strings.bookMarkUnfinished)
                            }
                            IconButton(
                                onClick = {
                                    onBatchDownload(selectedItems)
                                    multiSelectMode = false
                                    selectedBookIds = emptySet()
                                },
                                enabled = selectedItems.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Download, contentDescription = strings.downloadAction)
                            }
                        }
                    }
                }
            }
        }

        // Grid or List Content
        AnimatedContent(
            targetState = isGridView,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(160))
            },
            modifier = Modifier.fillMaxSize(),
            label = "library_view_mode_transition",
        ) { gridMode ->
            if (gridMode) {
                val minGridCellSize = when (prefs.coverSize) {
                    "sm" -> 124.dp
                    "lg" -> 184.dp
                    else -> 150.dp
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minGridCellSize),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(books, key = { it.id }) { book ->
                        val prog = progressMap[book.id] ?: book.userMediaProgress
                        val isDownloaded = downloadedBooks.containsKey(book.id)
                        val isSelected = book.id in selectedBookIds

                        Box(modifier = Modifier.animateItem()) {
                            LibraryGridItem(
                                book = book,
                                progress = prog,
                                isDownloaded = isDownloaded,
                                coverUrl = coverUrlProvider(book),
                                inPlayer = isPlayingItemId == book.id,
                                isPlaying = isPlaying && isPlayingItemId == book.id,
                                multiSelectMode = multiSelectMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                    } else {
                                        onOpenBook(book.id)
                                    }
                                },
                                onPlay = { onPlayBook(book.id) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(books, key = { it.id }) { book ->
                        val prog = progressMap[book.id] ?: book.userMediaProgress
                        val isDownloaded = downloadedBooks.containsKey(book.id)
                        val isSelected = book.id in selectedBookIds

                        Box(modifier = Modifier.animateItem()) {
                            LibraryListItem(
                                book = book,
                                progress = prog,
                                isDownloaded = isDownloaded,
                                coverUrl = coverUrlProvider(book),
                                multiSelectMode = multiSelectMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                    } else {
                                        onOpenBook(book.id)
                                    }
                                },
                                onPlay = { onPlayBook(book.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFacetDialog) {
        FacetFilterDialog(
            filterData = filterData,
            queryState = queryState,
            onQueryStateChange = onQueryStateChange,
            onDismiss = { showFacetDialog = false },
        )
    }

    if (showSaveViewDialog) {
        AlertDialog(
            onDismissRequest = { showSaveViewDialog = false },
            title = { Text(strings.libraryTitle) },
            text = {
                OutlinedTextField(
                    value = saveViewName,
                    onValueChange = { saveViewName = it },
                    placeholder = { Text(strings.textFilterPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = saveViewName.trim()
                        if (trimmed.isNotEmpty()) {
                            onSaveView(
                                SavedLibraryView(
                                    id = "view_${System.currentTimeMillis()}",
                                    name = trimmed,
                                    query = queryState.textQuery,
                                ),
                            )
                            saveViewName = ""
                            showSaveViewDialog = false
                        }
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveViewDialog = false }) {
                    Text(strings.playerClose)
                }
            },
        )
    }
}

@Composable
private fun LibraryGridItem(
    book: LibraryItem,
    progress: MediaProgress?,
    isDownloaded: Boolean,
    coverUrl: String?,
    inPlayer: Boolean,
    isPlaying: Boolean,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val colors = KoalaTheme.colors
    val strings = currentKoalaStrings
    val progFraction = (progress?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
    val isFinished = progress?.isFinished == true
    val collapsedSeries = book.collapsedSeries
    val isSeriesStack = collapsedSeries != null && collapsedSeries.numBooks > 1

    BookShelfTile(
        title = if (isSeriesStack) collapsedSeries.name else book.title,
        subtitle = if (isSeriesStack) {
            listOfNotNull(
                book.author.takeIf { it.isNotBlank() },
                strings.booksCount(collapsedSeries.numBooks),
            ).joinToString(" · ")
        } else {
            "${book.author} · ${formatDuration(book.durationSeconds, strings)}"
        },
        coverAuthor = book.author,
        coverUrl = coverUrl,
        progress = if (isSeriesStack) 0f else progFraction,
        isFinished = !isSeriesStack && isFinished,
        doneLabel = strings.filterFinished,
        seriesCountLabel = if (isSeriesStack) strings.booksCount(collapsedSeries.numBooks) else null,
        inPlayer = inPlayer,
        isPlaying = isPlaying,
        onClick = onClick,
        onPlayClick = if (multiSelectMode || isSeriesStack) null else onPlay,
        topStartOverlay = if (isDownloaded) {
            {
                Surface(
                    shape = CircleShape,
                    color = colors.panel.copy(alpha = 0.9f),
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = strings.downloadedLabel,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        } else {
            null
        },
        topEndOverlay = if (multiSelectMode) {
            {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(26.dp),
                )
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LibraryListItem(
    book: LibraryItem,
    progress: MediaProgress?,
    isDownloaded: Boolean,
    coverUrl: String?,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val colors = KoalaTheme.colors
    val strings = currentKoalaStrings
    val progFraction = (progress?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
    val isFinished = progress?.isFinished == true

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = book.title,
                author = book.author,
                coverUrl = coverUrl,
                progress = progFraction,
                isFinished = isFinished,
                modifier = Modifier.size(64.dp),
                cornerRadius = 10.dp,
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(book.durationSeconds, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    if (progFraction > 0.01f && !isFinished) {
                        Text(
                            text = "${(progFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                        )
                    }
                    if (isDownloaded) {
                        Text(
                            text = strings.downloadedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                        )
                    }
                }
            }

            if (multiSelectMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = colors.accent,
                )
            } else {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = strings.bookPlay,
                        tint = colors.accent,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetFilterDialog(
    filterData: FilterData,
    queryState: LibraryQueryState,
    onQueryStateChange: (LibraryQueryState) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = currentKoalaStrings

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.filterAll) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.filterLength, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        DurationPreset.ALL to strings.filterAll,
                        DurationPreset.LT2 to "< 2h",
                        DurationPreset.H2_6 to "2–6h",
                        DurationPreset.H6_12 to "6–12h",
                        DurationPreset.H12_24 to "12–24h",
                        DurationPreset.GT24 to "> 24h",
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = queryState.durationPreset == preset,
                            onClick = { onQueryStateChange(queryState.copy(durationPreset = preset)) },
                            label = { Text(label) },
                        )
                    }
                }

                if (filterData.genres.isNotEmpty()) {
                    Text(strings.filterGenre, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        filterData.genres.take(24).forEach { g ->
                            val selected = g in queryState.genres
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) queryState.genres - g else queryState.genres + g
                                    onQueryStateChange(queryState.copy(genres = next))
                                },
                                label = { Text(g) },
                            )
                        }
                    }
                }

                if (filterData.narrators.isNotEmpty()) {
                    Text(strings.filterNarrator, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        filterData.narrators.take(18).forEach { n ->
                            val selected = n in queryState.narrators
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) queryState.narrators - n else queryState.narrators + n
                                    onQueryStateChange(queryState.copy(narrators = next))
                                },
                                label = { Text(n) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onQueryStateChange(LibraryQueryState())
            }) {
                Text(strings.clearFilters)
            }
        },
    )
}
