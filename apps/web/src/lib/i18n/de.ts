import type { Messages } from './en.ts';

export const de: Messages = {
	appName: 'KoalaShelf',
	nav: {
		main: 'Hauptnavigation',
		home: 'Start',
		library: 'Bibliothek',
		series: 'Serien',
		authors: 'Autor:innen',
		search: 'Suche',
		signOut: 'Abmelden',
		themeSystem: 'Theme: System',
		themeLight: 'Theme: Hell',
		themeDark: 'Theme: Dunkel',
		language: 'Sprache'
	},
	signin: {
		title: 'Anmelden',
		intro: 'Melde dich mit deinem Audiobookshelf-Konto an.',
		server: 'Server',
		serverPlaceholder: 'https://hoerbuch.example.org',
		username: 'Benutzername',
		password: 'Passwort',
		submit: 'Anmelden',
		connecting: 'Verbinde …',
		privacy: 'Deine Daten bleiben zwischen diesem Gerät und deinem Server. Keine Tracker, keine fremden Dienste.',
		errWrongCredentials: 'Benutzername oder Passwort stimmt nicht.',
		errRateLimited: 'Zu viele Versuche. Bitte kurz warten.',
		errNotAbs: 'Unter dieser Adresse läuft kein Audiobookshelf.',
		errServer: (msg) => `Der Server meldet: ${msg}`,
		errUnreachable:
			'Server nicht erreichbar. Adresse prüfen. Läuft die Web-App auf einer anderen Adresse, muss sie in Audiobookshelf unter „Allowed Origins“ eingetragen sein.',
		errTooOld: (version, min) => `Audiobookshelf ${version} ist zu alt. KoalaShelf braucht mindestens ${min}.`,
		errUnknown: 'Unbekannter Fehler.'
	},
	library: {
		title: 'Bibliothek',
		count: (n) => `${n} Titel`,
		statusGroup: 'Status',
		all: 'Alle',
		inProgress: 'Laufend',
		notStarted: 'Neu',
		finished: 'Fertig',
		genre: 'Genre',
		narrator: 'Sprecher:in',
		series: 'Serie',
		sort: 'Sortierung',
		sortTitle: 'Titel',
		sortAuthor: 'Autor:in',
		sortAdded: 'Neu hinzugefügt',
		sortProgress: 'Zuletzt gehört',
		sortDuration: 'Dauer',
		sortPublished: 'Erscheinungsjahr',
		reverse: 'Reihenfolge umkehren',
		view: 'Ansicht',
		viewShelf: 'Regal',
		viewList: 'Liste',
		empty: 'Keine Titel für diese Auswahl.',
		loadError: (msg) => `Konnte die Bibliothek nicht laden: ${msg}`,
		loading: 'Lade …',
		readBy: (name) => `gelesen von ${name}`,
		listened: (p) => `${p} % gehört`,
		done: 'Gehört'
	},
	format: {
		hours: 'Std.',
		minutes: 'Min.'
	}
};

