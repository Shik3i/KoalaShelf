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
		language: 'Language'
	},
	signin: {
		title: 'Sign in',
		intro: 'Sign in with your Audiobookshelf account.',
		server: 'Server',
		serverPlaceholder: 'https://audiobooks.example.org',
		username: 'Username',
		password: 'Password',
		submit: 'Sign in',
		connecting: 'Connecting …',
		privacy: 'Your data stays between this device and your server. No trackers, no third-party services.',
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
