<script lang="ts">
	import type {
		Collection,
		LibraryItem,
		LibraryItemMinified,
		Playlist
	} from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Check from '@lucide/svelte/icons/check';
	import Minus from '@lucide/svelte/icons/minus';
	import Plus from '@lucide/svelte/icons/plus';

	/** Dialog to put one or several books into playlists or collections (or a new one). */
	let {
		kind = 'playlist',
		items,
		open = $bindable(false)
	}: {
		kind?: 'playlist' | 'collection';
		items: (LibraryItem | LibraryItemMinified)[];
		open?: boolean;
	} = $props();

	interface Entry {
		id: string;
		name: string;
		ids: string[];
	}

	const t = $derived(kind === 'playlist' ? i18n.m.playlists : i18n.m.collections);
	const api = session.api;
	let dialog = $state<HTMLDialogElement>();
	let lists = $state.raw<Entry[] | null>(null);
	let name = $state('');
	let busy = $state<string | null>(null);
	const wanted = $derived(items.map((i) => i.id));
	const libraryId = $derived(items[0]?.libraryId ?? session.libraryId ?? '');

	const entry = (x: Playlist | Collection): Entry =>
		'items' in x
			? { id: x.id, name: x.name, ids: x.items.map((i) => i.libraryItemId) }
			: { id: x.id, name: x.name, ids: x.books.map((b) => b.id) };

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) {
			dialog.showModal();
			lists = null;
			(kind === 'playlist' ? api.playlists(libraryId) : api.collections(libraryId))
				.then((l) => (lists = l.map(entry)))
				.catch(() => (lists = []));
		}
		if (!open && dialog.open) dialog.close();
	});

	/** All of the books are in it, some, or none. */
	const membership = (e: Entry): boolean | 'mixed' => {
		const n = wanted.filter((id) => e.ids.includes(id)).length;
		return n === wanted.length ? true : n ? 'mixed' : false;
	};

	async function toggle(e: Entry) {
		busy = e.id;
		try {
			const remove = membership(e) === true;
			const ids = remove ? wanted : wanted.filter((id) => !e.ids.includes(id));
			const single = ids.length === 1;
			const updated =
				kind === 'playlist'
					? remove
						? single
							? await api.removeFromPlaylist(e.id, ids[0])
							: await api.removeManyFromPlaylist(e.id, ids)
						: single
							? await api.addToPlaylist(e.id, ids[0])
							: await api.addManyToPlaylist(e.id, ids)
					: remove
						? single
							? await api.removeFromCollection(e.id, ids[0])
							: await api.removeManyFromCollection(e.id, ids)
						: single
							? await api.addToCollection(e.id, ids[0])
							: await api.addManyToCollection(e.id, ids);
			toasts.show(remove ? t.removedFrom(e.name) : t.addedTo(e.name));
			const next = entry(updated);
			// The server deletes a playlist once its last book is gone.
			lists =
				kind === 'playlist' && !next.ids.length
					? (lists ?? []).filter((x) => x.id !== e.id)
					: (lists ?? []).map((x) => (x.id === e.id ? next : x));
		} catch (err) {
			toasts.show(i18n.m.common.error((err as Error).message));
		} finally {
			busy = null;
		}
	}

	async function create(ev: SubmitEvent) {
		ev.preventDefault();
		const n = name.trim();
		if (!n) return;
		busy = 'new';
		try {
			const created =
				kind === 'playlist'
					? await api.createPlaylist(libraryId, n, wanted)
					: await api.createCollection(libraryId, n, wanted);
			lists = [...(lists ?? []), entry(created)];
			name = '';
			toasts.show(t.addedTo(created.name));
		} catch (err) {
			toasts.show(i18n.m.common.error((err as Error).message));
		} finally {
			busy = null;
		}
	}
</script>

<dialog bind:this={dialog} onclose={() => (open = false)} aria-labelledby="pick-title">
	<h2 id="pick-title">{t.addTitle}</h2>
	<p class="muted book">
		{items.length === 1 ? items[0].media.metadata.title : i18n.m.authors.books(items.length)}
	</p>
	{#if lists === null}
		<div class="skeleton" style="height: 96px; border-radius: 14px"></div>
	{:else}
		<ul>
			{#each lists as e (e.id)}
				{@const s = membership(e)}
				<li>
					<button
						role="checkbox"
						aria-checked={s}
						disabled={busy === e.id}
						onclick={() => toggle(e)}
					>
						<span class="box" class:on={s !== false} aria-hidden="true"
							>{#if s === true}<Check size={14} />{:else if s}<Minus size={14} />{/if}</span
						>
						<span class="name">{e.name}</span>
						<span class="muted n">{e.ids.length}</span>
					</button>
				</li>
			{:else}
				<li class="muted empty">{t.none}</li>
			{/each}
		</ul>
	{/if}
	<form onsubmit={create}>
		<input bind:value={name} placeholder={t.newName} aria-label={t.newName} maxlength="80" />
		<button class="btn primary" type="submit" disabled={!name.trim() || busy === 'new'}>
			<Plus size={16} aria-hidden="true" />
			{t.create}
		</button>
	</form>
	<button class="btn ghost close" onclick={() => (open = false)}>{t.done}</button>
</dialog>

<style>
	dialog {
		width: min(92vw, 440px);
		padding: var(--sp-24);
		border: 0;
		border-radius: var(--r-card-lg);
		background: var(--c-card);
		color: var(--c-ink);
		box-shadow: 0 30px 60px -20px rgb(0 0 0 / 0.5);
	}

	dialog[open] {
		display: grid;
		gap: var(--sp-12);
		animation: pop var(--motion-base) var(--motion-spring);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 0.35);
		backdrop-filter: blur(2px);
	}

	@keyframes pop {
		from {
			opacity: 0;
			scale: 0.94;
		}
	}

	h2 {
		font-size: var(--fs-24);
	}

	.book {
		margin: 0;
		font-size: var(--fs-14);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
		max-height: 50dvh;
		overflow: auto;
	}

	li button {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		width: 100%;
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		color: inherit;
		text-align: left;
		font-weight: var(--fw-semibold);
	}

	li button:hover {
		background: var(--c-bg);
	}

	.box {
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		flex: none;
		border: 2px solid var(--c-muted);
		border-radius: 6px;
	}

	.box.on {
		border-color: var(--c-accent);
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.n,
	.empty {
		font-size: var(--fs-13);
	}

	.empty {
		padding: var(--sp-8) var(--sp-12);
	}

	form {
		display: flex;
		gap: var(--sp-8);
	}

	input {
		flex: 1;
		min-width: 0;
		min-height: var(--layout-touch);
		padding: 0 var(--sp-16);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-pill);
		background: var(--c-bg);
		color: var(--c-ink);
		font: inherit;
	}

	input:focus-visible {
		outline: none;
		border-color: var(--c-accent);
	}

	.close {
		justify-self: end;
	}
</style>
