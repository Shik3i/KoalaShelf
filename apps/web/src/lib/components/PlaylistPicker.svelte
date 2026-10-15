<script lang="ts">
	import type { LibraryItem, Playlist } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Check from '@lucide/svelte/icons/check';
	import Plus from '@lucide/svelte/icons/plus';

	/** Dialog to put one book into the listener's playlists (or a new one). */
	let { item, open = $bindable(false) }: { item: LibraryItem; open?: boolean } = $props();

	const t = $derived(i18n.m.playlists);
	let dialog = $state<HTMLDialogElement>();
	let lists = $state.raw<Playlist[] | null>(null);
	let name = $state('');
	let busy = $state<string | null>(null);

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) {
			dialog.showModal();
			lists = null;
			session.api
				.playlists(item.libraryId)
				.then((l) => (lists = l))
				.catch(() => (lists = []));
		}
		if (!open && dialog.open) dialog.close();
	});

	const has = (p: Playlist) => p.items.some((i) => i.libraryItemId === item.id);

	async function toggle(p: Playlist) {
		busy = p.id;
		try {
			const updated = has(p)
				? await session.api.removeFromPlaylist(p.id, item.id)
				: await session.api.addToPlaylist(p.id, item.id);
			toasts.show(has(p) ? t.removedFrom(p.name) : t.addedTo(p.name));
			lists = (lists ?? []).map((x) => (x.id === p.id ? updated : x));
		} finally {
			busy = null;
		}
	}

	async function create(e: SubmitEvent) {
		e.preventDefault();
		const n = name.trim();
		if (!n) return;
		busy = 'new';
		try {
			const p = await session.api.createPlaylist(item.libraryId, n, [item.id]);
			lists = [...(lists ?? []), p];
			name = '';
			toasts.show(t.addedTo(p.name));
		} finally {
			busy = null;
		}
	}
</script>

<dialog bind:this={dialog} onclose={() => (open = false)} aria-labelledby="pl-title">
	<h2 id="pl-title">{t.addTitle}</h2>
	<p class="muted book">{item.media.metadata.title}</p>
	{#if lists === null}
		<div class="skeleton" style="height: 96px; border-radius: 14px"></div>
	{:else}
		<ul>
			{#each lists as p (p.id)}
				<li>
					<button
						role="checkbox"
						aria-checked={has(p)}
						disabled={busy === p.id}
						onclick={() => toggle(p)}
					>
						<span class="box" aria-hidden="true"
							>{#if has(p)}<Check size={14} />{/if}</span
						>
						<span class="name">{p.name}</span>
						<span class="muted n">{p.items.length}</span>
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

	[aria-checked='true'] .box {
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
