// Tiny in-memory cache so back/forward navigation renders instantly (stale-while-revalidate).
const store = new Map<string, { at: number; value: Promise<unknown> }>();

export function cached<T>(key: string, fn: () => Promise<T>, maxAgeMs = 60_000): Promise<T> {
	const hit = store.get(key);
	if (hit && Date.now() - hit.at < maxAgeMs) return hit.value as Promise<T>;
	const value = fn().catch((e) => {
		store.delete(key);
		throw e;
	});
	store.set(key, { at: Date.now(), value });
	return value;
}

export function invalidate(prefix = '') {
	for (const k of store.keys()) if (k.startsWith(prefix)) store.delete(k);
}
