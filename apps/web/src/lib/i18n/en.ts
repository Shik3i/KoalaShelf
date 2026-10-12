// English UI strings: the base locale. Every other locale must provide the same shape (type-checked).

const plural = (n: number, one: string, other: string) => `${n} ${n === 1 ? one : other}`;

export const en = {
	appName: 'KoalaShelf',
	nav: {
		main: 'Main navigation',
		home: 'Home',
		library: 'Library',
		series: 'Series',
		authors: 'Authors',
		search: 'Search',
		signOut: 'Sign out',
		themeSystem: 'Theme: system',
		themeLight: 'Theme: light',
		themeDark: 'Theme: dark',
		language: 'Language',
		settings: 'Settings'
	},
	signin: {
		title: 'Sign in',
		intro: 'Sign in with your Audiobookshelf account.',
		server: 'Audiobookshelf server',
		serverPlaceholder: 'https://audiobooks.example.org',
		serverChecking: 'Checking server …',
		serverOk: (version: string) => `Audiobookshelf ${version} found`,
		serverRecent: 'Recent servers',
		serverForget: 'Remove from list',
		errCors: (origin: string) =>
			`The server does not allow this app. In Audiobookshelf open Settings and add ${origin} to “Allowed origins” – or run KoalaShelf with Docker in front of your server.`,
		username: 'Username',
		password: 'Password',
		submit: 'Sign in',
		connecting: 'Connecting …',
		privacy:
			'Your data stays between this device and your server. No trackers, no third-party services.',
		errWrongCredentials: 'Wrong username or password.',
		errRateLimited: 'Too many attempts. Please wait a moment.',
		errNotAbs: 'There is no Audiobookshelf server at this address.',
		errServer: (msg: string) => `The server says: ${msg}`,
		errUnreachable:
			'Server not reachable. Check the address. If the web app runs on a different address, add it to “Allowed origins” in Audiobookshelf.',
		errTooOld: (version: string, min: string) =>
			`Audiobookshelf ${version} is too old. KoalaShelf needs at least ${min}.`,
		errUnknown: 'Unknown error.'
	},
	library: {
		title: 'Library',
		count: (n: number) => plural(n, 'title', 'titles'),
		statusGroup: 'Status',
		all: 'All',
		inProgress: 'In progress',
		notStarted: 'New',
		finished: 'Finished',
		genre: 'Genre',
		narrator: 'Narrator',
		series: 'Series',
		sort: 'Sort',
		sortTitle: 'Title',
		sortAuthor: 'Author',
		sortAdded: 'Recently added',
		sortProgress: 'Recently played',
		sortDuration: 'Duration',
		sortPublished: 'Year published',
		reverse: 'Reverse order',
		view: 'View',
		viewShelf: 'Shelf',
		viewList: 'List',
		empty: 'No titles match this selection.',
		loadError: (msg: string) => `Could not load the library: ${msg}`,
		loading: 'Loading …',
		readBy: (name: string) => `read by ${name}`,
		listened: (p: number) => `${p} % listened`,
		done: 'Finished'
	},
	book: {
		by: 'by',
		readBy: 'Narrated by',
		play: 'Play',
		resume: (time: string) => `Resume at ${time}`,
		listenAgain: 'Listen again',
		chapters: 'Chapters',
		chapterCount: (n: number) => plural(n, 'chapter', 'chapters'),
		bookmarks: 'Bookmarks',
		moreByAuthor: 'More by the author',
		noBookmarks: 'No bookmarks yet. Add one while listening.',
		description: 'Description',
		details: 'Details',
		format: 'Format',
		size: 'Size',
		genre: 'Genre',
		published: 'Published',
		publisher: 'Publisher',
		language: 'Language',
		library: 'Library',
		files: (n: number) => plural(n, 'file', 'files'),
		remaining: (time: string) => `${time} left`,
		listened: (p: number) => `${p} %`,
		markFinished: 'Mark as finished',
		markUnfinished: 'Mark as not finished',
		back: 'Back',
		notFound: 'This title does not exist (anymore).',
		seriesBook: (name: string, seq: string | null) => (seq ? `${name} · Book ${seq}` : name)
	},
	player: {
		player: 'Player',
		play: 'Play',
		pause: 'Pause',
		back: (s: number) => `Back ${s} seconds`,
		forward: (s: number) => `Forward ${s} seconds`,
		prevChapter: 'Previous chapter',
		nextChapter: 'Next chapter',
		speed: 'Speed',
		timer: 'Sleep timer',
		timerOff: 'Off',
		timerEndOfChapter: 'End of chapter',
		timerMinutes: (n: number) => `${n} min`,
		timerLeft: (t: string) => `Stops in ${t}`,
		timerAtChapterEnd: 'Stops at chapter end',
		chapters: 'Chapters',
		bookmarks: 'Bookmarks',
		addBookmark: 'Add bookmark',
		bookmarkTitle: 'Note',
		bookmarkPlaceholder: 'What happens here?',
		save: 'Save',
		delete: 'Delete',
		cancel: 'Cancel',
		close: 'Close',
		open: 'Open player',
		chapterOf: (i: number, n: number) => `Chapter ${i} of ${n}`,
		remainingBook: (t: string) => `${t} left in book`,
		remainingChapter: (t: string) => `${t} left in chapter`,
		seek: 'Position',
		error: 'Playback failed. Retrying …',
		errorFatal: 'Playback failed. Press play to try again.',
		edit: 'Edit note',
		syncFailed: 'Progress could not be saved. Will retry.',
		shortcuts: 'Space play/pause · ← → skip · Shift ← → chapter · B bookmark'
	},
	home: {
		greetingMorning: 'Good morning',
		greetingDay: 'Hello',
		greetingEvening: 'Good evening',
		continue: 'Continue listening',
		continueSeries: 'Continue series',
		recentlyAdded: 'Recently added',
		recentSeries: 'Recent series',
		discover: 'Discover',
		listenAgain: 'Listen again',
		newestAuthors: 'Newest authors',
		thisWeek: 'this week',
		empty: 'Nothing here yet. Start something from the library.',
		toLibrary: 'Go to library'
	},
	series: {
		title: 'Series',
		count: (n: number) => plural(n, 'series', 'series'),
		books: (n: number) => plural(n, 'book', 'books'),
		empty: 'No series in this library.'
	},
	authors: {
		title: 'Authors',
		books: (n: number) => plural(n, 'book', 'books'),
		empty: 'No authors in this library.'
	},
	search: {
		title: 'Search',
		placeholder: 'Titles, authors, narrators, series …',
		books: 'Books',
		series: 'Series',
		authors: 'Authors',
		narrators: 'Narrators',
		nothing: (q: string) => `Nothing found for “${q}”.`,
		hint: 'Search across your library.'
	},
	settings: {
		title: 'Settings',
		appearance: 'Appearance',
		theme: 'Theme',
		themeSystem: 'System',
		themeLight: 'Light',
		themeDark: 'Dark',
		language: 'Language',
		playback: 'Playback',
		skipBack: 'Skip back',
		skipForward: 'Skip forward',
		seconds: (n: number) => `${n} s`,
		smartRewind: 'Smart rewind',
		smartRewindHint:
			'After a longer pause, playback starts a few seconds earlier (5 s after 1 min, 15 s after 10 min, 30 s after 1 h).',
		account: 'Account',
		user: 'User',
		server: 'Audiobookshelf server',
		version: 'Version',
		signOut: 'Sign out',
		about: 'About',
		aboutText:
			'KoalaShelf is an open-source client for Audiobookshelf. It talks only to your server: no trackers, no analytics, no third-party requests.',
		source: 'Source code',
		on: 'On',
		off: 'Off'
	},
	common: {
		error: (msg: string) => `Something went wrong: ${msg}`,
		retry: 'Retry',
		loading: 'Loading …'
	},
	format: {
		hours: 'h',
		minutes: 'min'
	}
};

/** Widens literal string types so translations can use their own wording. */
type Widen<T> = T extends string
	? string
	: T extends (...args: infer A) => string
		? (...args: A) => string
		: { [K in keyof T]: Widen<T[K]> };

export type Messages = Widen<typeof en>;
