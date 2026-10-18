// "Your year" as a picture to keep or share: drawn on this device (canvas), never uploaded.
import type { LibraryItem, YearStats } from '@koalashelf/abs-client';
import { i18n } from './i18n/index.svelte.ts';
import { duration } from './format.ts';
import { coverSrc } from './items.ts';

function loadImage(src: string): Promise<HTMLImageElement | null> {
	return new Promise((resolve) => {
		const img = new Image();
		img.crossOrigin = 'anonymous';
		img.onload = () => resolve(img);
		img.onerror = () => resolve(null);
		img.src = src;
		setTimeout(() => resolve(null), 8000);
	});
}

function rounded(
	ctx: CanvasRenderingContext2D,
	x: number,
	y: number,
	w: number,
	h: number,
	r: number
) {
	ctx.beginPath();
	ctx.roundRect(x, y, w, h, r);
}

/** Longest prefix of `text` that fits `max` pixels, with an ellipsis when shortened. */
function fit(ctx: CanvasRenderingContext2D, text: string, max: number) {
	if (ctx.measureText(text).width <= max) return text;
	let s = text;
	while (s.length > 1 && ctx.measureText(`${s}…`).width > max) s = s.slice(0, -1);
	return `${s.trimEnd()}…`;
}

export type CardFormat = 'story' | 'post';

export async function renderYearCard(
	year: number,
	data: YearStats,
	covers: LibraryItem[],
	cardFormat: CardFormat = 'story'
) {
	const isStory = cardFormat === 'story';
	const W = 1080;
	const H = isStory ? 1920 : 1350;
	const t = i18n.m.yearReview;
	const css = getComputedStyle(document.documentElement);
	const v = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
	const accent = v('--c-accent', '#c2410c');
	const onAccent = v('--c-on-accent', '#ffffff');
	const display = v('--font-display', 'serif');
	const body = getComputedStyle(document.body).fontFamily || 'sans-serif';
	await document.fonts?.ready;

	const canvas = document.createElement('canvas');
	canvas.width = W;
	canvas.height = H;
	const ctx = canvas.getContext('2d')!;

	// Dark record-sleeve background with an accent glow.
	ctx.fillStyle = '#14161d';
	ctx.fillRect(0, 0, W, H);
	try {
		const glow = ctx.createRadialGradient(W * 0.15, 0, 40, W * 0.15, 0, W * 1.1);
		glow.addColorStop(0, accent);
		glow.addColorStop(1, 'transparent');
		ctx.globalAlpha = 0.45;
		ctx.fillStyle = glow;
		ctx.fillRect(0, 0, W, H);
	} catch {
		/* a color the canvas cannot parse: no glow */
	}
	ctx.globalAlpha = 1;

	// In Story format, add a subtle warm bottom vignette for vinyl warmth
	if (isStory) {
		try {
			const bottomGlow = ctx.createRadialGradient(W * 0.85, H, 50, W * 0.85, H, W * 0.9);
			bottomGlow.addColorStop(0, accent);
			bottomGlow.addColorStop(1, 'transparent');
			ctx.globalAlpha = 0.25;
			ctx.fillStyle = bottomGlow;
			ctx.fillRect(0, 0, W, H);
			ctx.globalAlpha = 1;
		} catch {
			/* ignore */
		}
	}

	// A record in the corner.
	ctx.save();
	ctx.translate(W - 40, 40);
	for (let r = 300; r > 70; r -= 9) {
		ctx.beginPath();
		ctx.arc(0, 0, r, 0, Math.PI * 2);
		ctx.strokeStyle = r % 2 ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.02)';
		ctx.lineWidth = 4;
		ctx.stroke();
	}
	ctx.beginPath();
	ctx.arc(0, 0, 70, 0, Math.PI * 2);
	ctx.fillStyle = accent;
	ctx.fill();
	ctx.restore();

	const pad = 88;
	ctx.fillStyle = '#ffffff';
	ctx.textBaseline = 'alphabetic';
	ctx.font = `700 34px ${body}`;
	ctx.fillStyle = 'rgba(255,255,255,0.7)';
	ctx.fillText('KOALASHELF', pad, 130);
	ctx.fillStyle = '#ffffff';
	// Title on up to two lines, clear of the record.
	ctx.font = `400 92px ${display}`;
	const words = t.cardTitle(year).split(' ');
	const lines: string[] = [];
	const maxW = W - pad * 2 - 60;
	for (const w of words) {
		const last = lines.at(-1);
		if (last !== undefined && ctx.measureText(`${last} ${w}`).width <= maxW)
			lines[lines.length - 1] = `${last} ${w}`;
		else lines.push(w);
	}
	const title = lines.length > 2 ? [lines[0], fit(ctx, lines.slice(1).join(' '), maxW)] : lines;
	title.forEach((line, i) => ctx.fillText(line, pad, 240 + i * 100));
	const top = 240 + (title.length - 1) * 100;

	// Big numbers.
	const numbers: [string, string][] = [
		[duration(data.totalListeningTime), t.listened],
		[String(data.numBooksFinished), t.finished],
		[String(data.numBooksListened), t.books]
	];
	let y = top + 150;
	for (const [value, label] of numbers) {
		ctx.font = `400 88px ${display}`;
		ctx.fillStyle = '#ffffff';
		ctx.fillText(fit(ctx, value, W - pad * 2), pad, y);
		ctx.font = `600 32px ${body}`;
		ctx.fillStyle = 'rgba(255,255,255,0.7)';
		ctx.fillText(label, pad, y + 46);
		y += 140;
	}

	// Favourites: author, genre, narrator.
	const facts: [string, string | undefined][] = [
		[t.topAuthors, data.topAuthors[0]?.name],
		[t.topGenres, data.topGenres[0]?.genre],
		[t.narrator, data.mostListenedNarrator?.name]
	];
	y = isStory ? 1120 : Math.max(900, y - 140 + 46 + 90);
	for (const [label, value] of facts) {
		if (!value) continue;
		ctx.font = `700 26px ${body}`;
		ctx.fillStyle = accent;
		ctx.fillText(label.toUpperCase(), pad, y);
		ctx.font = `600 40px ${body}`;
		ctx.fillStyle = '#ffffff';
		ctx.fillText(fit(ctx, value, W / 2 - pad), pad, y + 50);
		y += 110;
	}

	// Covers of the year, a small stack on the right.
	const images = (
		await Promise.all(
			covers
				.slice(0, 6)
				.map((c) => coverSrc(c))
				.filter((src): src is (w: number) => string => !!src)
				.map((src) => loadImage(src(300)))
		)
	).filter((i): i is HTMLImageElement => !!i);
	const size = 210;
	images.forEach((img, i) => {
		const col = i % 2;
		const row = Math.floor(i / 2);
		const x = W - pad - size * 2 - 24 + col * (size + 24);
		const yy = (isStory ? 1100 : 860) + row * (size + 24) - (col ? 40 : 0);
		ctx.save();
		ctx.shadowColor = 'rgba(0,0,0,0.5)';
		ctx.shadowBlur = 30;
		ctx.shadowOffsetY = 12;
		rounded(ctx, x, yy, size, size, 18);
		ctx.fillStyle = '#000';
		ctx.fill();
		ctx.restore();
		ctx.save();
		rounded(ctx, x, yy, size, size, 18);
		ctx.clip();
		ctx.drawImage(img, x, yy, size, size);
		ctx.restore();
	});

	// Footer pill.
	ctx.font = `700 28px ${body}`;
	const foot = t.cardFooter;
	const fw = ctx.measureText(foot).width + 56;
	rounded(ctx, pad, H - 130, fw, 64, 32);
	ctx.fillStyle = accent;
	ctx.fill();
	ctx.fillStyle = onAccent;
	ctx.fillText(foot, pad + 28, H - 87);

	return new Promise<Blob>((resolve, reject) =>
		canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('canvas'))), 'image/png')
	);
}
