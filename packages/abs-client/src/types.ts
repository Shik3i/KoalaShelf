// Audiobookshelf API types (subset used by KoalaShelf), verified against server v2.37.

export interface ServerStatus {
	app: 'audiobookshelf';
	serverVersion: string;
	isInit: boolean;
	language: string;
	authMethods: string[];
}

export interface User {
	id: string;
	username: string;
	type: 'root' | 'admin' | 'user' | 'guest';
	mediaProgress: MediaProgress[];
	bookmarks: Bookmark[];
	librariesAccessible: string[];
	accessToken: string;
	refreshToken: string | null;
}

export interface LoginResponse {
	user: User;
	userDefaultLibraryId: string;
	serverSettings: Record<string, unknown>;
}

export interface Library {
	id: string;
	name: string;
	displayOrder: number;
	icon: string;
	mediaType: 'book' | 'podcast';
}

export interface AuthorRef {
	id: string;
	name: string;
}

export interface SeriesRef {
	id: string;
	name: string;
	sequence: string | null;
}

export interface BookMetadata {
	title: string;
	subtitle: string | null;
	authors: AuthorRef[];
	narrators: string[];
	series: SeriesRef[];
	genres: string[];
	publishedYear: string | null;
	publishedDate: string | null;
	publisher: string | null;
	description: string | null;
	isbn: string | null;
	asin: string | null;
	language: string | null;
	explicit: boolean;
}

/** Minified items carry flattened name strings instead of arrays. */
export interface BookMetadataMinified {
	title: string;
	subtitle: string | null;
	authorName: string;
	narratorName: string;
	seriesName: string;
	genres: string[];
	publishedYear: string | null;
	language: string | null;
	description?: string | null;
}

export interface Chapter {
	id: number;
	start: number;
	end: number;
	title: string;
}

export interface FileMetadata {
	filename: string;
	ext: string;
	size: number;
}

export interface AudioFile {
	index: number;
	ino: string;
	format: string;
	codec: string;
	bitRate: number;
	duration: number;
	mimeType: string;
	metadata: FileMetadata;
}

export interface AudioTrack {
	index: number;
	startOffset: number;
	duration: number;
	title: string;
	contentUrl: string;
	mimeType: string;
	metadata?: FileMetadata;
}

export interface Book {
	metadata: BookMetadata;
	coverPath: string | null;
	tags: string[];
	audioFiles: AudioFile[];
	chapters: Chapter[];
	tracks?: AudioTrack[];
	duration: number;
	size: number;
}

export interface BookMinified {
	metadata: BookMetadataMinified;
	coverPath: string | null;
	duration: number;
	size: number;
	numChapters?: number;
	numAudioFiles?: number;
}

interface LibraryItemBase {
	id: string;
	libraryId: string;
	mediaType: 'book' | 'podcast';
	addedAt: number;
	updatedAt: number;
	isMissing: boolean;
	isInvalid: boolean;
}

export interface LibraryItem extends LibraryItemBase {
	media: Book;
	size: number;
	userMediaProgress?: MediaProgress;
}

export interface LibraryItemMinified extends LibraryItemBase {
	media: BookMinified;
	/** Present when collapseseries=1 and the item stands for a series. */
	collapsedSeries?: { id: string; name: string; nameIgnorePrefix: string; numBooks: number };
}

export interface MediaProgress {
	id: string;
	libraryItemId: string;
	episodeId: string | null;
	duration: number;
	progress: number;
	currentTime: number;
	isFinished: boolean;
	hideFromContinueListening: boolean;
	lastUpdate: number;
	startedAt: number;
	finishedAt: number | null;
}

export interface Bookmark {
	libraryItemId: string;
	title: string;
	time: number;
	createdAt: number;
}

export interface Paged<T> {
	results: T[];
	total: number;
	limit: number;
	page: number;
}

export interface Series {
	id: string;
	name: string;
	nameIgnorePrefix?: string;
	addedAt: number;
	books: LibraryItemMinified[];
}

export interface Author {
	id: string;
	name: string;
	description: string | null;
	imagePath: string | null;
	numBooks?: number;
	updatedAt: number;
}

export interface FilterData {
	authors: AuthorRef[];
	genres: string[];
	tags: string[];
	series: { id: string; name: string }[];
	narrators: string[];
	languages: string[];
}

export type ShelfId =
	| 'continue-listening'
	| 'continue-series'
	| 'recently-added'
	| 'recent-series'
	| 'recommended'
	| 'listen-again'
	| 'newest-authors'
	| (string & {});

export type Shelf =
	| { id: ShelfId; label: string; type: 'book'; entities: LibraryItemMinified[] }
	| { id: ShelfId; label: string; type: 'series'; entities: Series[] }
	| { id: ShelfId; label: string; type: 'authors'; entities: Author[] }
	| { id: ShelfId; label: string; type: 'episode' | 'podcast'; entities: unknown[] };

export interface DeviceInfo {
	deviceId: string;
	clientName: string;
	clientVersion: string;
	manufacturer?: string;
	model?: string;
}

export interface PlaybackSession {
	id: string;
	libraryItemId: string;
	displayTitle: string;
	displayAuthor: string;
	duration: number;
	playMethod: number;
	currentTime: number;
	startTime: number;
	chapters: Chapter[];
	audioTracks: AudioTrack[];
	libraryItem: LibraryItem;
}

export interface ProgressUpdate {
	currentTime?: number;
	duration?: number;
	progress?: number;
	isFinished?: boolean;
}

export interface SessionSync {
	currentTime: number;
	timeListened: number;
	duration: number;
}
