package net.koalastuff.koalashelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.koalastuff.koalashelf.core.data.i18n.AppLocale
import net.koalastuff.koalashelf.core.data.i18n.LocalKoalaStrings
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.i18n.stringsForLocale
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.repository.ChapterSearchHit
import net.koalastuff.koalashelf.core.data.repository.DownloadRepository
import net.koalastuff.koalashelf.core.data.repository.LibraryQueryState
import net.koalastuff.koalashelf.core.data.repository.LibraryRepository
import net.koalastuff.koalashelf.core.data.repository.ProgressRepository
import net.koalastuff.koalashelf.core.data.repository.QueueRepository
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.data.util.formatLocalDay
import net.koalastuff.koalashelf.core.designsystem.KoalaAccentPreset
import net.koalastuff.koalashelf.core.designsystem.KoalaFontChoice
import net.koalastuff.koalashelf.core.designsystem.KoalaMotionMode
import net.koalastuff.koalashelf.core.designsystem.KoalaShelfTheme
import net.koalastuff.koalashelf.core.designsystem.KoalaTextSize
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.KoalaThemeMode
import net.koalastuff.koalashelf.core.network.AuthSession
import net.koalastuff.koalashelf.core.network.Author
import net.koalastuff.koalashelf.core.network.AuthorDetail
import net.koalastuff.koalashelf.core.network.Bookmark
import net.koalastuff.koalashelf.core.network.Collection
import net.koalastuff.koalashelf.core.network.FilterData
import net.koalastuff.koalashelf.core.network.LibraryItem
import net.koalastuff.koalashelf.core.network.LibraryStats
import net.koalastuff.koalashelf.core.network.ListeningSession
import net.koalastuff.koalashelf.core.network.ListeningStats
import net.koalastuff.koalashelf.core.network.Narrator
import net.koalastuff.koalashelf.core.network.Playlist
import net.koalastuff.koalashelf.core.network.SearchResult
import net.koalastuff.koalashelf.core.network.Series
import net.koalastuff.koalashelf.core.network.Shelf
import net.koalastuff.koalashelf.core.network.YearStats
import net.koalastuff.koalashelf.core.player.KoalaPlayerController
import net.koalastuff.koalashelf.feature.auth.AccountSwitcherSheet
import net.koalastuff.koalashelf.feature.auth.SignInScreen
import net.koalastuff.koalashelf.feature.home.HomeScreen
import net.koalastuff.koalashelf.feature.library.AuthorDetailScreen
import net.koalastuff.koalashelf.feature.library.AuthorsScreen
import net.koalastuff.koalashelf.feature.library.BookDetailScreen
import net.koalastuff.koalashelf.feature.library.BookmarksScreen
import net.koalastuff.koalashelf.feature.library.CollectionsAndPlaylistsScreen
import net.koalastuff.koalashelf.feature.library.DownloadsScreen
import net.koalastuff.koalashelf.feature.library.LibraryScreen
import net.koalastuff.koalashelf.feature.library.SearchScreen
import net.koalastuff.koalashelf.feature.library.SeriesDetailScreen
import net.koalastuff.koalashelf.feature.library.SeriesScreen
import net.koalastuff.koalashelf.feature.library.SettingsScreen
import net.koalastuff.koalashelf.feature.library.StatsAndHistoryScreen
import net.koalastuff.koalashelf.feature.player.FullPlayerSheet
import net.koalastuff.koalashelf.feature.player.MiniPlayerBar
import org.koin.android.ext.android.inject
import java.util.Calendar

sealed interface KoalaRoute {
    data object Home : KoalaRoute
    data object Library : KoalaRoute
    data object Series : KoalaRoute
    data object Authors : KoalaRoute
    data object Collections : KoalaRoute
    data object Playlists : KoalaRoute
    data object Search : KoalaRoute
    data object Bookmarks : KoalaRoute
    data object Downloads : KoalaRoute
    data object Stats : KoalaRoute
    data object Settings : KoalaRoute
    data class BookDetail(val bookId: String) : KoalaRoute
    data class SeriesDetail(val seriesId: String, val seriesName: String) : KoalaRoute
    data class AuthorDetailRoute(val authorId: String, val authorName: String) : KoalaRoute
}

class MainActivity : ComponentActivity() {

    private val prefsRepository: PrefsRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val progressRepository: ProgressRepository by inject()
    private val queueRepository: QueueRepository by inject()
    private val downloadRepository: DownloadRepository by inject()
    private val libraryRepository: LibraryRepository by inject()
    private val playerController: KoalaPlayerController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by prefsRepository.prefs.collectAsState()
            val locale = AppLocale.fromTag(prefs.locale)
            val strings = stringsForLocale(locale)

            CompositionLocalProvider(LocalKoalaStrings provides strings) {
                KoalaShelfTheme(
                    themeMode = KoalaThemeMode.fromId(prefs.themeMode),
                    accentPreset = KoalaAccentPreset.fromId(prefs.accent),
                    customAccentHex = prefs.customAccent,
                    fontChoice = KoalaFontChoice.fromId(prefs.font),
                    textSize = KoalaTextSize.fromId(prefs.textSize),
                    motionMode = KoalaMotionMode.fromId(prefs.motion),
                    spinEnabled = prefs.spin,
                ) {
                    KoalaAppShell(
                        prefsRepository = prefsRepository,
                        sessionRepository = sessionRepository,
                        progressRepository = progressRepository,
                        queueRepository = queueRepository,
                        downloadRepository = downloadRepository,
                        libraryRepository = libraryRepository,
                        playerController = playerController,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !playerController.playing.value) {
            playerController.release()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun KoalaAppShell(
    prefsRepository: PrefsRepository,
    sessionRepository: SessionRepository,
    progressRepository: ProgressRepository,
    queueRepository: QueueRepository,
    downloadRepository: DownloadRepository,
    libraryRepository: LibraryRepository,
    playerController: KoalaPlayerController,
) {
    val scope = rememberCoroutineScope()
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    val prefs by prefsRepository.prefs.collectAsState()
    val activeAccount by sessionRepository.activeAccount.collectAsState()
    val activeKey by sessionRepository.activeKey.collectAsState()
    val libraries by sessionRepository.libraries.collectAsState()
    val activeLibraryId by sessionRepository.activeLibraryId.collectAsState()
    val progressMap by progressRepository.byItem.collectAsState()
    val queueEntries by queueRepository.items.collectAsState()
    val downloadedBooksMap by downloadRepository.books.collectAsState()
    val downloadJobsMap by downloadRepository.jobs.collectAsState()

    val playingItem by playerController.item.collectAsState()
    val isPlaying by playerController.playing.collectAsState()
    val playerExpanded by playerController.expanded.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val initialRoute = remember {
        if (prefs.startPage == "library") KoalaRoute.Library else KoalaRoute.Home
    }
    val backStack = remember { mutableStateListOf<KoalaRoute>(initialRoute) }
    val currentRoute = backStack.lastOrNull() ?: initialRoute

    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showAccountSwitcherSheet by remember { mutableStateOf(false) }
    var addingAccountFromAuthed by remember { mutableStateOf(false) }

    // Loaded library data states
    var homeShelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var libraryQueryState by remember { mutableStateOf(LibraryQueryState()) }
    var libraryBooks by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var filterData by remember { mutableStateOf(FilterData()) }
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var authorsList by remember { mutableStateOf<List<Author>>(emptyList()) }
    var narratorsList by remember { mutableStateOf<List<Narrator>>(emptyList()) }
    var collections by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var bookmarksWithBooks by remember { mutableStateOf<List<Pair<Bookmark, LibraryItem?>>>(emptyList()) }
    var listeningStats by remember { mutableStateOf(ListeningStats()) }
    var libraryStats by remember { mutableStateOf<LibraryStats?>(null) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var yearInReview by remember { mutableStateOf<YearStats?>(null) }
    var recentSessions by remember { mutableStateOf<List<ListeningSession>>(emptyList()) }
    var authSessions by remember { mutableStateOf<List<AuthSession>>(emptyList()) }

    // Detail screen dynamic states
    var detailedBook by remember { mutableStateOf<LibraryItem?>(null) }
    var detailBookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var moreByAuthorBooks by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var bookSessions by remember { mutableStateOf<List<ListeningSession>>(emptyList()) }
    var currentSeriesBooks by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var currentAuthorDetail by remember { mutableStateOf<AuthorDetail?>(null) }

    // Search screen states
    var searchQuery by remember { mutableStateOf("") }
    var searchAllLibraries by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf(SearchResult()) }
    var chapterHits by remember { mutableStateOf<List<ChapterSearchHit>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun navigateTo(route: KoalaRoute) {
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun navigateRoot(route: KoalaRoute) {
        backStack.clear()
        backStack.add(route)
    }

    fun popBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        popBack()
    }

    // Forward Player Controller Toast events to Snackbar
    LaunchedEffect(playerController) {
        playerController.toastEvents.collect { evt ->
            val res = snackbarHostState.showSnackbar(
                message = evt.message,
                actionLabel = evt.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) {
                evt.onAction?.invoke()
            }
        }
    }

    suspend fun refreshActiveLibraryData(libId: String) {
        homeShelves = libraryRepository.getHomeShelves(libId)
        filterData = libraryRepository.getFilterData(libId)
        libraryBooks = libraryRepository.queryLibraryItems(libId, libraryQueryState)
        listeningStats = libraryRepository.getListeningStats() ?: ListeningStats()
    }

    // Load library data when authenticated or active library changes
    LaunchedEffect(activeKey, activeLibraryId) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        addingAccountFromAuthed = false
        refreshActiveLibraryData(libId)
    }

    // Re-query library items when queryState changes
    LaunchedEffect(activeLibraryId, libraryQueryState) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        libraryBooks = libraryRepository.queryLibraryItems(libId, libraryQueryState)
    }

    // Load route-specific data
    LaunchedEffect(currentRoute, activeLibraryId) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        when (val r = currentRoute) {
            is KoalaRoute.Series -> {
                seriesList = libraryRepository.getSeriesList(libId)
            }
            is KoalaRoute.SeriesDetail -> {
                val (_, books) = libraryRepository.getSeriesDetail(libId, r.seriesId)
                currentSeriesBooks = books
            }
            is KoalaRoute.Authors -> {
                authorsList = libraryRepository.getAuthors(libId)
                narratorsList = libraryRepository.getNarrators(libId)
            }
            is KoalaRoute.AuthorDetailRoute -> {
                currentAuthorDetail = libraryRepository.getAuthorDetail(r.authorId, libId)
            }
            is KoalaRoute.Collections -> {
                collections = libraryRepository.getCollections(libId)
            }
            is KoalaRoute.Playlists -> {
                playlists = libraryRepository.getPlaylists(libId)
            }
            is KoalaRoute.Bookmarks -> {
                bookmarksWithBooks = libraryRepository.getAllBookmarksWithBooks()
            }
            is KoalaRoute.Stats -> {
                listeningStats = libraryRepository.getListeningStats() ?: ListeningStats()
                libraryStats = libraryRepository.getLibraryStats(libId)
                yearInReview = libraryRepository.getYearStats(selectedYear)
                recentSessions = libraryRepository.getListeningSessions().sessions
            }
            is KoalaRoute.Settings -> {
                authSessions = libraryRepository.getAuthSessions()
            }
            is KoalaRoute.BookDetail -> {
                detailedBook = libraryBooks.firstOrNull { it.id == r.bookId }
                val fetched = libraryRepository.getBookDetail(r.bookId)
                if (fetched != null) {
                    detailedBook = fetched
                    val author = fetched.author
                    moreByAuthorBooks = libraryBooks.filter {
                        it.id != fetched.id && it.author.equals(author, ignoreCase = true)
                    }
                }
                detailBookmarks = runCatching {
                    sessionRepository.apiClient?.bookmarks(r.bookId) ?: emptyList()
                }.getOrDefault(emptyList())
                bookSessions = libraryRepository.getItemListeningSessions(r.bookId).sessions
                collections = libraryRepository.getCollections(libId)
                playlists = libraryRepository.getPlaylists(libId)
            }
            else -> {}
        }
    }

    // Live search execution
    LaunchedEffect(searchQuery, searchAllLibraries, activeLibraryId) {
        searchJob?.cancel()
        val libId = activeLibraryId ?: return@LaunchedEffect
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2) {
            searchResult = SearchResult()
            chapterHits = emptyList()
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(250L)
            val (res, chHits) = libraryRepository.search(
                libraryId = libId,
                query = trimmed,
                allLibraries = searchAllLibraries,
                includeChapters = true,
            )
            searchResult = res
            chapterHits = chHits
        }
    }

    val coverUrlProvider: (LibraryItem) -> String? = remember(activeKey, downloadedBooksMap) {
        { item ->
            downloadRepository.localCoverFile(item.id)?.absolutePath
                ?: sessionRepository.apiClient?.coverUrl(item.id, width = 400, updatedAt = item.updatedAt)
        }
    }

    if (activeAccount == null || addingAccountFromAuthed) {
        SignInScreen(
            sessionRepository = sessionRepository,
            prefsRepository = prefsRepository,
            isAddingAccount = addingAccountFromAuthed,
            onSignedIn = { addingAccountFromAuthed = false },
            onCancelAdd = { addingAccountFromAuthed = false },
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = colors.bg,
        contentColor = colors.ink,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MiniPlayerBar(
                    playerController = playerController,
                    prefs = prefs,
                    coverUrlProvider = coverUrlProvider,
                    onExpand = { playerController.setExpanded(true) },
                )

                // Mobile bottom navigation bar matching AppNav.svelte: Home, Library, Downloads, Search, More
                NavigationBar(
                    containerColor = colors.panel,
                    contentColor = colors.ink,
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.ink,
                        indicatorColor = colors.sel,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                    )
                    NavigationBarItem(
                        selected = currentRoute is KoalaRoute.Home,
                        onClick = { navigateRoot(KoalaRoute.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = strings.navHome) },
                        label = { Text(strings.navHome) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute is KoalaRoute.Library,
                        onClick = { navigateRoot(KoalaRoute.Library) },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = strings.navLibrary) },
                        label = { Text(strings.navLibrary) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute is KoalaRoute.Downloads,
                        onClick = { navigateRoot(KoalaRoute.Downloads) },
                        icon = { Icon(Icons.Default.Download, contentDescription = strings.navDownloads) },
                        label = { Text(strings.navDownloads) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute is KoalaRoute.Search,
                        onClick = { navigateRoot(KoalaRoute.Search) },
                        icon = { Icon(Icons.Default.Search, contentDescription = strings.navSearch) },
                        label = { Text(strings.navSearch) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute in listOf(
                            KoalaRoute.Series,
                            KoalaRoute.Authors,
                            KoalaRoute.Collections,
                            KoalaRoute.Playlists,
                            KoalaRoute.Bookmarks,
                            KoalaRoute.Stats,
                            KoalaRoute.Settings,
                        ) || currentRoute is KoalaRoute.SeriesDetail || currentRoute is KoalaRoute.AuthorDetailRoute,
                        onClick = { showMoreMenuSheet = true },
                        icon = { Icon(Icons.Default.MoreHoriz, contentDescription = strings.navMore) },
                        label = { Text(strings.navMore) },
                        colors = navItemColors,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val route = currentRoute) {
                is KoalaRoute.Home -> {
                    val todayKey = remember { formatLocalDay(System.currentTimeMillis()) }
                    val todayListeningSeconds = listeningStats.days[todayKey] ?: listeningStats.today

                    HomeScreen(
                        username = activeAccount?.user?.username ?: "",
                        libraries = libraries,
                        activeLibraryId = activeLibraryId ?: "",
                        shelves = homeShelves,
                        allBooks = libraryBooks,
                        progressMap = progressMap,
                        todayListeningSeconds = todayListeningSeconds,
                        prefs = prefs,
                        isPlayingItemId = playingItem?.id,
                        isPlaying = isPlaying,
                        coverUrlProvider = coverUrlProvider,
                        onSelectLibrary = { libId -> sessionRepository.selectLibrary(libId) },
                        onRefresh = {
                            val libId = activeLibraryId ?: return@HomeScreen
                            scope.launch { refreshActiveLibraryData(libId) }
                        },
                        onOpenBook = { bookId -> navigateTo(KoalaRoute.BookDetail(bookId)) },
                        onPlayBook = { bookId -> playerController.play(bookId) },
                        onOpenSeries = { sid, sname -> navigateTo(KoalaRoute.SeriesDetail(sid, sname)) },
                        onOpenAuthor = { aid, aname -> navigateTo(KoalaRoute.AuthorDetailRoute(aid, aname)) },
                        onOpenStats = { navigateTo(KoalaRoute.Stats) },
                        onOpenSettings = { navigateTo(KoalaRoute.Settings) },
                        onHideFromContinue = { progressId ->
                            val libId = activeLibraryId ?: return@HomeScreen
                            scope.launch {
                                libraryRepository.hideFromContinueListening(progressId)
                                refreshActiveLibraryData(libId)
                            }
                        },
                        onSetDailyGoalMinutes = { mins ->
                            prefsRepository.update { it.copy(dailyGoal = mins) }
                        },
                    )
                }

                is KoalaRoute.Library -> {
                    LibraryScreen(
                        books = libraryBooks,
                        queryState = libraryQueryState,
                        onQueryStateChange = { libraryQueryState = it },
                        filterData = filterData,
                        progressMap = progressMap,
                        downloadedBooks = downloadedBooksMap,
                        prefs = prefs,
                        isPlayingItemId = playingItem?.id,
                        isPlaying = isPlaying,
                        coverUrlProvider = coverUrlProvider,
                        onOpenBook = { bookId -> navigateTo(KoalaRoute.BookDetail(bookId)) },
                        onPlayBook = { bookId -> playerController.play(bookId) },
                        onSaveView = { view ->
                            prefsRepository.update { it.copy(savedViews = it.savedViews + view) }
                        },
                        onDeleteSavedView = { viewId ->
                            prefsRepository.update { p -> p.copy(savedViews = p.savedViews.filterNot { it.id == viewId }) }
                        },
                        onBatchQueue = { selected ->
                            selected.forEach { queueRepository.add(it) }
                        },
                        onBatchMarkFinished = { selected, finished ->
                            scope.launch { libraryRepository.markBatchFinished(selected, finished) }
                        },
                        onBatchDownload = { selected ->
                            selected.forEach { downloadRepository.enqueueDownload(it.id) }
                        },
                    )
                }

                is KoalaRoute.BookDetail -> {
                    val book = detailedBook ?: libraryBooks.firstOrNull { it.id == route.bookId }
                    if (book != null) {
                        val prog = progressMap[book.id] ?: book.userMediaProgress
                        val dlBook = downloadedBooksMap[book.id]
                        val dlJob = downloadJobsMap[book.id]
                        val isQueued = queueEntries.any { it.id == book.id }

                        BookDetailScreen(
                            book = book,
                            progress = prog,
                            downloadedBook = dlBook,
                            downloadJob = dlJob,
                            bookmarks = detailBookmarks,
                            moreByAuthor = moreByAuthorBooks,
                            bookSessions = bookSessions,
                            collections = collections,
                            playlists = playlists,
                            prefs = prefs,
                            isPlayingThisBook = isPlaying && playingItem?.id == book.id,
                            coverUrlProvider = coverUrlProvider,
                            onBack = { popBack() },
                            onPlayBook = { startSec ->
                                playerController.play(book.id, startAtSeconds = startSec)
                            },
                            onToggleQueue = {
                                if (isQueued) queueRepository.remove(book.id) else queueRepository.add(book)
                            },
                            isQueued = isQueued,
                            onMarkFinished = { finished ->
                                scope.launch { libraryRepository.setBookFinished(book, finished) }
                            },
                            onResetProgress = {
                                scope.launch { libraryRepository.resetBookProgress(book.id) }
                            },
                            onStartDownload = {
                                downloadRepository.enqueueDownload(book.id)
                            },
                            onRemoveDownload = {
                                scope.launch { downloadRepository.remove(book.id) }
                            },
                            onAddBookmark = { time, title ->
                                scope.launch {
                                    val created = sessionRepository.apiClient?.createBookmark(book.id, time, title)
                                    if (created != null) {
                                        detailBookmarks = (detailBookmarks.filterNot { it.time == time } + created).sortedBy { it.time }
                                    }
                                }
                            },
                            onDeleteBookmark = { time ->
                                scope.launch {
                                    sessionRepository.apiClient?.deleteBookmark(book.id, time)
                                    detailBookmarks = detailBookmarks.filterNot { it.time == time }
                                }
                            },
                            onAddToCollection = { colId ->
                                scope.launch { libraryRepository.addToCollection(colId, book.id) }
                            },
                            onAddToPlaylist = { plId ->
                                scope.launch { libraryRepository.addToPlaylist(plId, book.id) }
                            },
                            onOpenSeries = { sid, sname -> navigateTo(KoalaRoute.SeriesDetail(sid, sname)) },
                            onOpenAuthor = { aid, aname -> navigateTo(KoalaRoute.AuthorDetailRoute(aid, aname)) },
                            onOpenOtherBook = { otherId -> navigateTo(KoalaRoute.BookDetail(otherId)) },
                            onSearch = { query ->
                                searchQuery = query
                                navigateTo(KoalaRoute.Search)
                            },
                        )
                    }
                }

                is KoalaRoute.Series -> {
                    SeriesScreen(
                        seriesList = seriesList,
                        progressMap = progressMap,
                        coverUrlProvider = coverUrlProvider,
                        onOpenSeries = { sid, sname -> navigateTo(KoalaRoute.SeriesDetail(sid, sname)) },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                    )
                }

                is KoalaRoute.SeriesDetail -> {
                    val seriesBooks = currentSeriesBooks.ifEmpty {
                        seriesList.firstOrNull { it.id == route.seriesId }?.books.orEmpty()
                    }
                    SeriesDetailScreen(
                        seriesId = route.seriesId,
                        seriesName = route.seriesName,
                        books = seriesBooks,
                        progressMap = progressMap,
                        coverUrlProvider = coverUrlProvider,
                        onBack = { popBack() },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onPlayBook = { bid -> playerController.play(bid) },
                        onDownloadAll = { booksToDl ->
                            booksToDl.forEach { downloadRepository.enqueueDownload(it.id) }
                        },
                    )
                }

                is KoalaRoute.Authors -> {
                    AuthorsScreen(
                        authors = authorsList,
                        narrators = narratorsList,
                        onOpenAuthor = { aid, aname -> navigateTo(KoalaRoute.AuthorDetailRoute(aid, aname)) },
                        onSelectNarratorBooks = { narratorName ->
                            libraryQueryState = LibraryQueryState(narrators = setOf(narratorName))
                            navigateRoot(KoalaRoute.Library)
                        },
                    )
                }

                is KoalaRoute.AuthorDetailRoute -> {
                    val booksByAuthor = remember(libraryBooks, currentAuthorDetail, route.authorName) {
                        val detailItems = currentAuthorDetail?.libraryItems.orEmpty()
                        if (detailItems.isNotEmpty()) detailItems
                        else libraryBooks.filter { it.author.contains(route.authorName, ignoreCase = true) }
                    }
                    AuthorDetailScreen(
                        authorName = route.authorName,
                        authorDetail = currentAuthorDetail,
                        books = booksByAuthor,
                        coverUrlProvider = coverUrlProvider,
                        onBack = { popBack() },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onPlayBook = { bid -> playerController.play(bid) },
                    )
                }

                is KoalaRoute.Collections -> {
                    val libId = activeLibraryId ?: ""
                    CollectionsAndPlaylistsScreen(
                        isPlaylistsMode = false,
                        collections = collections,
                        playlists = playlists,
                        coverUrlProvider = coverUrlProvider,
                        onCreate = { name, desc ->
                            scope.launch {
                                libraryRepository.createCollection(libId, name, emptyList(), desc)
                                collections = libraryRepository.getCollections(libId)
                            }
                        },
                        onDelete = { id ->
                            scope.launch {
                                libraryRepository.deleteCollection(id)
                                collections = libraryRepository.getCollections(libId)
                            }
                        },
                        onRemoveBook = { colId, bookId ->
                            scope.launch {
                                libraryRepository.removeFromCollection(colId, bookId)
                                collections = libraryRepository.getCollections(libId)
                            }
                        },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onPlayBook = { bid -> playerController.play(bid) },
                    )
                }

                is KoalaRoute.Playlists -> {
                    val libId = activeLibraryId ?: ""
                    CollectionsAndPlaylistsScreen(
                        isPlaylistsMode = true,
                        collections = collections,
                        playlists = playlists,
                        coverUrlProvider = coverUrlProvider,
                        onCreate = { name, _ ->
                            scope.launch {
                                libraryRepository.createPlaylist(libId, name, emptyList())
                                playlists = libraryRepository.getPlaylists(libId)
                            }
                        },
                        onDelete = { id ->
                            scope.launch {
                                libraryRepository.deletePlaylist(id)
                                playlists = libraryRepository.getPlaylists(libId)
                            }
                        },
                        onRemoveBook = { plId, bookId ->
                            scope.launch {
                                libraryRepository.removeFromPlaylist(plId, bookId)
                                playlists = libraryRepository.getPlaylists(libId)
                            }
                        },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onPlayBook = { bid -> playerController.play(bid) },
                    )
                }

                is KoalaRoute.Search -> {
                    SearchScreen(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        searchAllLibraries = searchAllLibraries,
                        onToggleScope = { searchAllLibraries = it },
                        searchResult = searchResult,
                        chapterHits = chapterHits,
                        coverUrlProvider = coverUrlProvider,
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onOpenAuthor = { aid, aname -> navigateTo(KoalaRoute.AuthorDetailRoute(aid, aname)) },
                        onOpenSeries = { sid, sname -> navigateTo(KoalaRoute.SeriesDetail(sid, sname)) },
                        onFilterLibraryByNarrator = { narr ->
                            libraryQueryState = LibraryQueryState(narrators = setOf(narr))
                            navigateRoot(KoalaRoute.Library)
                        },
                        onFilterLibraryByGenre = { genre ->
                            libraryQueryState = LibraryQueryState(genres = setOf(genre))
                            navigateRoot(KoalaRoute.Library)
                        },
                        onFilterLibraryByTag = { tag ->
                            libraryQueryState = LibraryQueryState(tags = setOf(tag))
                            navigateRoot(KoalaRoute.Library)
                        },
                        onPlayBookAt = { book, startSec ->
                            playerController.play(book.id, startAtSeconds = startSec)
                        },
                    )
                }

                is KoalaRoute.Bookmarks -> {
                    BookmarksScreen(
                        bookmarksWithBooks = bookmarksWithBooks,
                        markdownExportProvider = { libraryRepository.exportBookmarksMarkdown(bookmarksWithBooks) },
                        csvExportProvider = { libraryRepository.exportBookmarksCsv(bookmarksWithBooks) },
                        onJumpToBookmark = { bookId, timeSec ->
                            playerController.play(bookId, startAtSeconds = timeSec)
                        },
                        onDeleteBookmark = { bookId, timeSec ->
                            scope.launch {
                                sessionRepository.apiClient?.deleteBookmark(bookId, timeSec)
                                bookmarksWithBooks = libraryRepository.getAllBookmarksWithBooks()
                            }
                        },
                        onShowToast = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                }

                is KoalaRoute.Downloads -> {
                    val dlList = remember(downloadedBooksMap) {
                        downloadedBooksMap.values.sortedByDescending { it.downloadedAt }
                    }
                    val radar = remember(downloadedBooksMap) {
                        downloadRepository.storageRadar()
                    }
                    DownloadsScreen(
                        downloadedBooks = dlList,
                        downloadJobs = downloadJobsMap,
                        storageRadar = radar,
                        progressMap = progressMap,
                        onPlayOfflineBook = { bookId -> playerController.play(bookId) },
                        onDeleteDownload = { bookId ->
                            scope.launch { downloadRepository.remove(bookId) }
                        },
                        onCleanFinishedDownloads = {
                            scope.launch {
                                dlList.filter { progressMap[it.id]?.isFinished == true }.forEach {
                                    downloadRepository.remove(it.id)
                                }
                            }
                        },
                    )
                }

                is KoalaRoute.Stats -> {
                    val finishedBooksCount = remember(progressMap) {
                        progressMap.values.count { it.isFinished }
                    }
                    StatsAndHistoryScreen(
                        stats = listeningStats,
                        yearInReview = yearInReview,
                        selectedYear = selectedYear,
                        finishedBooksCount = finishedBooksCount,
                        sessions = recentSessions,
                        libraryStats = libraryStats,
                        historyCsvExportProvider = { libraryRepository.exportHistoryCsv(recentSessions) },
                        onSelectYear = { year ->
                            selectedYear = year
                            scope.launch { yearInReview = libraryRepository.getYearStats(year) }
                        },
                        onOpenBook = { bid -> navigateTo(KoalaRoute.BookDetail(bid)) },
                        onShowToast = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                }

                is KoalaRoute.Settings -> {
                    val activeAccountLabel = "${activeAccount?.user?.username ?: ""} @ ${activeAccount?.label ?: ""}"
                    SettingsScreen(
                        prefs = prefs,
                        prefsRepository = prefsRepository,
                        activeAccountLabel = activeAccountLabel,
                        authSessions = authSessions,
                        onOpenAccountSwitcher = { showAccountSwitcherSheet = true },
                        onSignOutDevice = { sessionId ->
                            scope.launch {
                                libraryRepository.signOutDevice(sessionId)
                                authSessions = libraryRepository.getAuthSessions()
                            }
                        },
                        onChangePassword = { currentPwd, newPwd ->
                            scope.launch {
                                val ok = libraryRepository.changePassword(currentPwd, newPwd)
                                snackbarHostState.showSnackbar(
                                    if (ok) strings.passwordChanged else strings.passwordChangeError
                                )
                            }
                        },
                        onShowToast = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                }
            }
        }
    }

    // Full Player Modal Sheet
    if (playerExpanded && playingItem != null) {
        FullPlayerSheet(
            playerController = playerController,
            prefsRepository = prefsRepository,
            queueRepository = queueRepository,
            coverUrlProvider = coverUrlProvider,
            onDismiss = { playerController.setExpanded(false) },
            onOpenBookDetail = { bookId -> navigateTo(KoalaRoute.BookDetail(bookId)) },
        )
    }

    // More Navigation Menu Sheet (matching AppNav.svelte .more-sheet)
    if (showMoreMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenuSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.card,
            contentColor = colors.ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Active Account Header with Switch Button
                activeAccount?.let { acct ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.panel)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acct.user.username, style = MaterialTheme.typography.titleMedium)
                            Text(acct.label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        OutlinedButton(
                            onClick = {
                                showMoreMenuSheet = false
                                showAccountSwitcherSheet = true
                            },
                        ) {
                            Icon(Icons.Default.SupervisorAccount, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(strings.accountSwitch)
                        }
                    }
                }

                val moreMenuEntries = listOf(
                    Triple(KoalaRoute.Series, Icons.Default.Album, strings.navSeries) to "series",
                    Triple(KoalaRoute.Authors, Icons.Default.Person, strings.navAuthors) to "authors",
                    Triple(KoalaRoute.Stats, Icons.Default.BarChart, strings.navHistory) to "history",
                    Triple(KoalaRoute.Bookmarks, Icons.Default.Bookmark, strings.navBookmarks) to "bookmarks",
                    Triple(KoalaRoute.Collections, Icons.Default.CollectionsBookmark, strings.navCollections) to "collections",
                    Triple(KoalaRoute.Playlists, Icons.AutoMirrored.Filled.PlaylistPlay, strings.navPlaylists) to "playlists",
                    Triple(KoalaRoute.Settings, Icons.Default.Settings, strings.navSettings) to "settings",
                )
                moreMenuEntries.forEach { (item, navKey) ->
                    if (navKey != "settings" && navKey in prefs.navHidden) return@forEach
                    val (route, icon, label) = item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreMenuSheet = false
                                navigateTo(route)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = colors.accent)
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Library Switcher Pills when > 1 library
                if (libraries.size > 1) {
                    HorizontalDivider(color = colors.line)
                    Text(
                        text = strings.librarySwitch,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        libraries.forEach { lib ->
                            FilterChip(
                                selected = lib.id == activeLibraryId,
                                onClick = {
                                    sessionRepository.selectLibrary(lib.id)
                                    showMoreMenuSheet = false
                                },
                                label = { Text(lib.name) },
                            )
                        }
                    }
                }

                // Quick Theme & Language Switcher in More Sheet (AppNav parity)
                HorizontalDivider(color = colors.line)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "system" to strings.settingsThemeSystem,
                            "light" to strings.settingsThemeLight,
                            "dark" to strings.settingsThemeDark,
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { prefsRepository.update { it.copy(themeMode = mode) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("de" to "DE", "en" to "EN").forEach { (code, label) ->
                            FilterChip(
                                selected = prefs.locale == code,
                                onClick = { prefsRepository.update { it.copy(locale = code) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Multi-Account & Server Switcher Sheet
    if (showAccountSwitcherSheet) {
        AccountSwitcherSheet(
            sessionRepository = sessionRepository,
            onDismiss = { showAccountSwitcherSheet = false },
            onAddAccount = {
                showAccountSwitcherSheet = false
                addingAccountFromAuthed = true
            },
        )
    }
}
