package net.koalastuff.koalashelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import net.koalastuff.koalashelf.core.designsystem.ProvideKoalaHaptics
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
import net.koalastuff.koalashelf.core.network.ServerUser
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
            val prefs by prefsRepository.prefs.collectAsStateWithLifecycle()
            val locale = AppLocale.fromTag(prefs.locale)
            val strings = stringsForLocale(locale)

            CompositionLocalProvider(LocalKoalaStrings provides strings) {
                ProvideKoalaHaptics(enabled = prefs.haptics) {
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = currentKoalaStrings
    val colors = KoalaTheme.colors

    val prefs by prefsRepository.prefs.collectAsStateWithLifecycle()
    val activeAccount by sessionRepository.activeAccount.collectAsStateWithLifecycle()
    val activeKey by sessionRepository.activeKey.collectAsStateWithLifecycle()
    val libraries by sessionRepository.libraries.collectAsStateWithLifecycle()
    val activeLibraryId by sessionRepository.activeLibraryId.collectAsStateWithLifecycle()
    val progressMap by progressRepository.byItem.collectAsStateWithLifecycle()
    val queueEntries by queueRepository.items.collectAsStateWithLifecycle()
    val downloadedBooksMap by downloadRepository.books.collectAsStateWithLifecycle()
    val downloadJobsMap by downloadRepository.jobs.collectAsStateWithLifecycle()

    val playingItem by playerController.item.collectAsStateWithLifecycle()
    val isPlaying by playerController.playing.collectAsStateWithLifecycle()
    val playerExpanded by playerController.expanded.collectAsStateWithLifecycle()

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
    var serverUsers by remember { mutableStateOf<List<ServerUser>>(emptyList()) }
    var selectedStatsUserId by remember { mutableStateOf<String?>(null) }

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

    suspend fun refreshActiveLibraryData(libId: String, includeBooks: Boolean = true) {
        runCatching {
            coroutineScope {
                val shelvesDef = async { libraryRepository.getHomeShelves(libId) }
                val filterDef = async { libraryRepository.getFilterData(libId) }
                val statsDef = async { libraryRepository.getListeningStats() ?: ListeningStats() }
                val booksDef = if (includeBooks) {
                    async { libraryRepository.queryLibraryItems(libId, libraryQueryState) }
                } else {
                    null
                }
                homeShelves = shelvesDef.await()
                filterData = filterDef.await()
                listeningStats = statsDef.await()
                if (booksDef != null) {
                    libraryBooks = booksDef.await()
                }
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            android.util.Log.e("KoalaShelf", "Failed to refresh library data for $libId", err)
            snackbarHostState.showSnackbar(
                message = "Bibliothek konnte nicht geladen werden: ${err.message ?: "Verbindungsfehler"}",
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Reset admin user selection when active account changes
    LaunchedEffect(activeKey) {
        selectedStatsUserId = null
        if (activeAccount?.isAdmin == true) {
            serverUsers = libraryRepository.getUsers()
        } else {
            serverUsers = emptyList()
        }
    }

    // Load library shelves, filterData, and stats when authenticated or active library changes
    LaunchedEffect(activeKey, activeLibraryId) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        addingAccountFromAuthed = false
        refreshActiveLibraryData(libId, includeBooks = false)
    }

    // Query library items when active library or queryState changes
    LaunchedEffect(activeKey, activeLibraryId, libraryQueryState) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        runCatching {
            libraryBooks = libraryRepository.queryLibraryItems(libId, libraryQueryState)
        }.onFailure { err ->
            if (err is CancellationException) throw err
            android.util.Log.e("KoalaShelf", "Query items failed", err)
        }
    }

    // Load route-specific data
    LaunchedEffect(currentRoute, activeLibraryId) {
        val libId = activeLibraryId ?: return@LaunchedEffect
        runCatching {
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
                    if (activeAccount?.isAdmin == true) {
                        serverUsers = libraryRepository.getUsers()
                    }
                    listeningStats = libraryRepository.getListeningStats(selectedStatsUserId) ?: ListeningStats()
                    libraryStats = libraryRepository.getLibraryStats(libId)
                    yearInReview = libraryRepository.getYearStats(selectedYear)
                    recentSessions = libraryRepository.getListeningSessions(userId = selectedStatsUserId).sessions
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
                        withContext(Dispatchers.IO) {
                            sessionRepository.apiClient?.bookmarks(r.bookId) ?: emptyList()
                        }
                    }.getOrDefault(emptyList())
                    bookSessions = libraryRepository.getItemListeningSessions(r.bookId).sessions
                    collections = libraryRepository.getCollections(libId)
                    playlists = libraryRepository.getPlaylists(libId)
                }
                else -> {}
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            android.util.Log.e("KoalaShelf", "Failed loading data for route $currentRoute", err)
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
                ?: if (item.hasCover) {
                    sessionRepository.apiClient?.coverUrl(item.id, width = 400, updatedAt = item.updatedAt)
                } else {
                    null
                }
        }
    }

    val reducedMotion = KoalaTheme.reducedMotion
    val isShowingAuth = activeAccount == null || addingAccountFromAuthed

    AnimatedContent(
        targetState = isShowingAuth,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240))) togetherWith
                    fadeOut(tween(180))
            }
        },
        label = "auth_shell_transition",
    ) { showAuth ->
        if (showAuth) {
            SignInScreen(
                sessionRepository = sessionRepository,
                prefsRepository = prefsRepository,
                isAddingAccount = addingAccountFromAuthed,
                onSignedIn = { addingAccountFromAuthed = false },
                onCancelAdd = { addingAccountFromAuthed = false },
            )
            return@AnimatedContent
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
            val canDownloadBook by sessionRepository.canDownload.collectAsStateWithLifecycle()
            AnimatedContent(
                targetState = currentRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val isPush = targetState is KoalaRoute.BookDetail ||
                            targetState is KoalaRoute.SeriesDetail ||
                            targetState is KoalaRoute.AuthorDetailRoute
                        val isPop = initialState is KoalaRoute.BookDetail ||
                            initialState is KoalaRoute.SeriesDetail ||
                            initialState is KoalaRoute.AuthorDetailRoute
                        when {
                            isPush && !isPop -> {
                                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 10 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 20 })
                            }
                            isPop && !isPush -> {
                                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { -it / 20 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 10 })
                            }
                            else -> {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                            }
                        }
                    }
                },
                label = "route_transition",
            ) { route ->
                when (route) {
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
                            canDownload = canDownloadBook,
                            onDownloadZip = {
                                val url = sessionRepository.apiClient?.downloadUrl(book.id)
                                if (!url.isNullOrBlank()) {
                                    runCatching {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
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
                        users = serverUsers,
                        selectedUserId = selectedStatsUserId,
                        isAdmin = activeAccount?.isAdmin == true,
                        onSelectUser = { uid ->
                            selectedStatsUserId = uid
                            scope.launch {
                                listeningStats = libraryRepository.getListeningStats(uid) ?: ListeningStats()
                                recentSessions = libraryRepository.getListeningSessions(userId = uid).sessions
                            }
                        },
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
