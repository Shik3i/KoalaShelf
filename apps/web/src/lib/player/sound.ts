// Sound processing through Web Audio: volume boost, mono, clearer voices, volume leveling,
// silence detection (for skipping silence) and analog vinyl warmth (needle drop & crackle).
//
// The <audio> element is routed through Web Audio once (createMediaElementSource cannot be
// undone); switching every effect off afterwards leaves a plain pass-through.

import type { SoundProfile } from '../prefs.svelte.ts';

export type { SoundProfile } from '../prefs.svelte.ts';

export interface SoundSettings {
	/** Even out loud and quiet passages (compressor). */
	leveling: boolean;
	/** Output gain: 1 = normal, up to 2 = twice as loud. */
	boost: number;
	/** Audio equalizer and scene profile preset. */
	profile: SoundProfile;
	/** Presence lift and rumble cut for speech (legacy). */
	voice?: boolean;
	/** Both channels mixed into one (one earbud, uneven stereo). */
	mono: boolean;
	/** Report silent stretches (the player then speeds through them). */
	silence: boolean;
	/** Subtle ambient vinyl surface crackle & warmth. */
	crackle: boolean;
	/** Vinyl crackle volume (0.01 - 0.15). */
	crackleVolume: number;
}

export const NO_SOUND: SoundSettings = {
	leveling: false,
	boost: 1,
	profile: 'off',
	voice: false,
	mono: false,
	silence: false,
	crackle: false,
	crackleVolume: 0.04
};

/** The compressor lowers peaks; this brings the overall level back up. */
const MAKEUP_GAIN = 1.6;
const WORKLET = '/worklets/silence.js';

/** True when any setting needs the Web Audio graph. */
export function needsGraph(s: SoundSettings) {
	return (
		s.leveling ||
		s.boost !== 1 ||
		(s.profile && s.profile !== 'off') ||
		s.voice ||
		s.mono ||
		s.silence ||
		s.crackle
	);
}

export class SoundChain {
	#ctx: AudioContext | null = null;
	#source: MediaElementAudioSourceNode | null = null;
	#mono: GainNode | null = null;
	#highpass: BiquadFilterNode | null = null;
	#presence: BiquadFilterNode | null = null;
	#warmth: BiquadFilterNode | null = null;
	#compressor: DynamicsCompressorNode | null = null;
	#gain: GainNode | null = null;
	#detector: AudioWorkletNode | null = null;
	#detectorLoading: Promise<void> | null = null;
	#settings: SoundSettings = NO_SOUND;
	#crackleSource: AudioBufferSourceNode | null = null;
	#crackleGain: GainNode | null = null;
	#isPlaying = false;

	/** Called with true when a silent stretch starts, false when sound returns. */
	onSilence: ((silent: boolean) => void) | null = null;

	/** True once the element plays through Web Audio (it then needs CORS for remote audio). */
	get attached() {
		return this.#ctx !== null;
	}

	/** Output node, for tests and meters. */
	get output() {
		return this.#gain;
	}

	get context() {
		return this.#ctx;
	}

	/** Silence detection runs in an audio worklet, which browsers only offer on https. */
	static get silenceAvailable() {
		return typeof AudioWorkletNode !== 'undefined' && globalThis.isSecureContext !== false;
	}

	attach(audio: HTMLAudioElement) {
		if (this.#source) return;
		const ctx = this.#ctx ?? new AudioContext({ latencyHint: 'playback' });
		this.#ctx = ctx;
		// Mixing to one channel: an explicit single-channel node downmixes, later nodes upmix.
		const mono = ctx.createGain();
		mono.channelCount = 1;
		mono.channelCountMode = 'explicit';
		mono.channelInterpretation = 'speakers';
		const highpass = ctx.createBiquadFilter();
		highpass.type = 'highpass';
		highpass.frequency.value = 90;
		const presence = ctx.createBiquadFilter();
		presence.type = 'peaking';
		presence.frequency.value = 2800;
		presence.Q.value = 0.9;
		presence.gain.value = 5;
		const warmth = ctx.createBiquadFilter();
		warmth.type = 'highshelf';
		warmth.frequency.value = 4500;
		warmth.gain.value = 0;
		const c = ctx.createDynamicsCompressor();
		// Speech-friendly: start compressing well below peaks, soft knee, moderate ratio.
		c.threshold.value = -32;
		c.knee.value = 12;
		c.ratio.value = 5;
		c.attack.value = 0.005;
		c.release.value = 0.3;
		this.#source = ctx.createMediaElementSource(audio);
		this.#mono = mono;
		this.#highpass = highpass;
		this.#presence = presence;
		this.#warmth = warmth;
		this.#compressor = c;
		this.#gain = ctx.createGain();
		this.#route();
		this.#updateCrackleState();
	}

	set(settings: SoundSettings) {
		this.#settings = { ...settings };
		this.#route();
		if (settings.silence) void this.#loadDetector();
		this.#updateCrackleState();
	}

	setPlaybackState(playing: boolean) {
		this.#isPlaying = playing;
		this.#updateCrackleState();
	}

	/** Plays a subtle authentic 0.48s vintage stylus needle drop sound cue without static hiss. */
	async playNeedleDrop() {
		let ctx = this.#ctx;
		if (!ctx) {
			try {
				ctx = new AudioContext({ latencyHint: 'playback' });
				this.#ctx = ctx;
			} catch {
				return;
			}
		}
		if (ctx.state === 'suspended') {
			await ctx.resume().catch(() => undefined);
		}
		try {
			const now = ctx.currentTime;
			const duration = 0.48;
			const buffer = ctx.createBuffer(1, Math.floor(ctx.sampleRate * duration), ctx.sampleRate);
			synthesizeNeedleDrop(buffer.getChannelData(0), ctx.sampleRate);
			const source = ctx.createBufferSource();
			source.buffer = buffer;
			const filter = ctx.createBiquadFilter();
			filter.type = 'lowpass';
			filter.frequency.value = 4800;
			const gain = ctx.createGain();
			gain.gain.setValueAtTime(0.24, now);
			source.connect(filter);
			filter.connect(gain);
			gain.connect(ctx.destination);
			source.start(now);
		} catch {
			// Ignore if buffer cannot be scheduled
		}
	}

	/** Synthesizes 2 revolutions (3.6s) of gentle locked-groove vinyl runout clicks before sleep. */
	async playRunOutGroove() {
		let ctx = this.#ctx;
		if (!ctx) {
			try {
				ctx = new AudioContext({ latencyHint: 'playback' });
				this.#ctx = ctx;
			} catch {
				return;
			}
		}
		if (ctx.state === 'suspended') {
			await ctx.resume().catch(() => undefined);
		}
		try {
			const now = ctx.currentTime;
			const duration = 3.6;
			const buffer = ctx.createBuffer(1, Math.floor(ctx.sampleRate * duration), ctx.sampleRate);
			const data = buffer.getChannelData(0);
			const samplesPerRev = Math.floor(ctx.sampleRate * 1.8);
			for (let i = 0; i < data.length; i++) {
				const t = i / ctx.sampleRate;
				const fade = Math.max(0, 1 - t / duration);
				const revPos = i % samplesPerRev;
				const dt = revPos / ctx.sampleRate;
				const thump =
					dt < 0.045 ? Math.sin(2 * Math.PI * (52 - dt * 280) * dt) * Math.exp(-dt * 55) * 0.42 : 0;
				const click =
					dt >= 0.002 && dt < 0.005
						? Math.sin(2 * Math.PI * 2400 * (dt - 0.002)) * Math.exp(-(dt - 0.002) * 1800) * 0.18
						: 0;
				data[i] = (thump + click) * fade;
			}
			const source = ctx.createBufferSource();
			source.buffer = buffer;
			const gain = ctx.createGain();
			gain.gain.setValueAtTime(0.18, now);
			source.connect(gain);
			gain.connect(ctx.destination);
			source.start(now);
		} catch {
			// Ignore if audio buffer cannot be scheduled
		}
	}

	/** Browsers start audio contexts suspended until a user gesture; call before play(). */
	async resume() {
		if (this.#ctx?.state === 'suspended') await this.#ctx.resume().catch(() => undefined);
	}

	async #loadDetector() {
		const ctx = this.#ctx;
		if (!ctx || this.#detector || !SoundChain.silenceAvailable) return;
		this.#detectorLoading ??= ctx.audioWorklet.addModule(WORKLET).then(() => {
			const node = new AudioWorkletNode(ctx, 'ks-silence', { numberOfOutputs: 1 });
			node.port.onmessage = (e: MessageEvent<boolean>) => this.onSilence?.(e.data);
			this.#detector = node;
			this.#route();
		});
		await this.#detectorLoading.catch(() => {
			this.#detectorLoading = null;
		});
	}

	#updateCrackleState() {
		const ctx = this.#ctx;
		if (!ctx) return;
		if (!this.#settings.crackle) {
			if (this.#crackleGain) {
				this.#crackleGain.gain.setTargetAtTime(0, ctx.currentTime, 0.1);
			}
			return;
		}

		if (!this.#crackleSource) {
			try {
				const sampleRate = ctx.sampleRate;
				// 4 vinyl revolutions at 33 1/3 RPM (1.8s per revolution = 7.2s loop)
				const length = Math.floor(sampleRate * 7.2);
				const buffer = ctx.createBuffer(1, length, sampleRate);
				synthesizeVinylCrackle(buffer.getChannelData(0), sampleRate);

				const src = ctx.createBufferSource();
				src.buffer = buffer;
				src.loop = true;

				const filter = ctx.createBiquadFilter();
				filter.type = 'lowpass';
				filter.frequency.value = 5200;
				filter.Q.value = 0.7;

				const gain = ctx.createGain();
				gain.gain.value = 0;

				src.connect(filter);
				filter.connect(gain);
				gain.connect(ctx.destination);
				src.start();

				this.#crackleSource = src;
				this.#crackleGain = gain;
			} catch {
				return;
			}
		}

		if (this.#crackleGain) {
			const target = this.#isPlaying ? this.#settings.crackleVolume : 0;
			this.#crackleGain.gain.setTargetAtTime(target, ctx.currentTime, 0.15);
		}
	}

	#route() {
		const ctx = this.#ctx;
		const src = this.#source;
		const gain = this.#gain;
		if (!ctx || !src || !gain) return;
		const s = this.#settings;
		const profile = s.profile ?? (s.voice ? 'voice' : 'off');
		const nodes = [
			this.#mono,
			this.#highpass,
			this.#presence,
			this.#warmth,
			this.#compressor,
			gain,
			this.#detector
		];
		src.disconnect();
		for (const n of nodes) n?.disconnect();
		const chain: AudioNode[] = [];
		if (s.mono) chain.push(this.#mono!);

		if (profile === 'voice') {
			this.#highpass!.frequency.value = 90;
			this.#presence!.type = 'peaking';
			this.#presence!.frequency.value = 2800;
			this.#presence!.Q.value = 0.9;
			this.#presence!.gain.value = 5;
			chain.push(this.#highpass!, this.#presence!);
		} else if (profile === 'night') {
			this.#highpass!.frequency.value = 120;
			this.#presence!.type = 'peaking';
			this.#presence!.frequency.value = 1400;
			this.#presence!.Q.value = 1.0;
			this.#presence!.gain.value = 3;
			this.#warmth!.type = 'highshelf';
			this.#warmth!.frequency.value = 4500;
			this.#warmth!.gain.value = -4;
			chain.push(this.#highpass!, this.#presence!, this.#warmth!);
		} else if (profile === 'commute') {
			this.#highpass!.frequency.value = 160;
			this.#presence!.type = 'peaking';
			this.#presence!.frequency.value = 3000;
			this.#presence!.Q.value = 1.2;
			this.#presence!.gain.value = 6.5;
			this.#warmth!.type = 'highshelf';
			this.#warmth!.frequency.value = 8000;
			this.#warmth!.gain.value = -2;
			chain.push(this.#highpass!, this.#presence!, this.#warmth!);
		} else if (profile === 'warmth') {
			this.#highpass!.frequency.value = 40;
			this.#presence!.type = 'peaking';
			this.#presence!.frequency.value = 2400;
			this.#presence!.Q.value = 0.8;
			this.#presence!.gain.value = 2.5;
			this.#warmth!.type = 'highshelf';
			this.#warmth!.frequency.value = 6500;
			this.#warmth!.gain.value = -3;
			chain.push(this.#highpass!, this.#presence!, this.#warmth!);
		}

		if (s.leveling || profile === 'commute') chain.push(this.#compressor!);
		chain.push(gain);
		gain.gain.value = (s.leveling || profile === 'commute' ? MAKEUP_GAIN : 1) * s.boost;
		let prev: AudioNode = src;
		for (const n of chain) prev = prev.connect(n);
		gain.connect(ctx.destination);
		// The detector listens to the source; its output is silent, it only reports.
		if (s.silence && this.#detector) {
			src.connect(this.#detector);
			this.#detector.connect(ctx.destination);
		} else if (!s.silence) this.onSilence?.(false);
	}
}

/**
 * Synthesizes authentic analog vinyl surface crackle, warm dust pops, and subtle
 * rotational groove-scratch wisps with zero continuous white/pink noise hiss.
 */
export function synthesizeVinylCrackle(
	data: Float32Array,
	sampleRate: number,
	rng: () => number = Math.random
) {
	data.fill(0);
	const length = data.length;
	if (length === 0 || sampleRate <= 0) return;
	const durationSec = length / sampleRate;

	// 1. Fine surface micro-ticks (crisp diamond-stylus dust transients, ~22/sec)
	const tickCount = Math.max(1, Math.round(durationSec * 22));
	const tickLen = Math.max(4, Math.ceil(sampleRate * 0.0012));
	for (let n = 0; n < tickCount; n++) {
		const start = Math.floor(rng() * Math.max(1, length - tickLen));
		const freq = 2600 + rng() * 2200;
		const decay = 3200 + rng() * 2400;
		const sign = rng() < 0.5 ? -1 : 1;
		const amp = (0.18 + rng() * 0.42) * sign;
		for (let k = 0; k < tickLen && start + k < length; k++) {
			const dt = k / sampleRate;
			data[start + k] += Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * decay) * amp;
		}
	}

	// 2. Warm analog vinyl dust pops (lower-mid damped groove impulses, ~4.5/sec)
	const popCount = Math.max(1, Math.round(durationSec * 4.5));
	const popLen = Math.max(8, Math.ceil(sampleRate * 0.0038));
	for (let n = 0; n < popCount; n++) {
		const start = Math.floor(rng() * Math.max(1, length - popLen));
		const freq = 520 + rng() * 580;
		const decay = 950 + rng() * 650;
		const sign = rng() < 0.5 ? -1 : 1;
		const amp = (0.35 + rng() * 0.55) * sign;
		for (let k = 0; k < popLen && start + k < length; k++) {
			const dt = k / sampleRate;
			data[start + k] += Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * decay) * amp;
		}
	}

	// 3. Subtle rotational groove-scratch wisps (1 brief hairline scuff per 1.8s revolution)
	const revolutions = Math.max(1, Math.floor(durationSec / 1.8));
	for (let rev = 0; rev < revolutions; rev++) {
		const revStartSec = rev * 1.8 + 0.25 + rng() * 1.2;
		const burstCount = 5 + Math.floor(rng() * 4);
		for (let b = 0; b < burstCount; b++) {
			const offsetSec = revStartSec + b * (0.0014 + rng() * 0.0012);
			const start = Math.floor(offsetSec * sampleRate);
			const env = Math.sin(((b + 0.5) / burstCount) * Math.PI);
			const freq = 1500 + rng() * 1300;
			const decay = 2200 + rng() * 1200;
			const amp = (0.16 + rng() * 0.18) * env * (b % 2 === 0 ? 1 : -1);
			const grainLen = Math.ceil(sampleRate * 0.0015);
			for (let k = 0; k < grainLen && start + k < length; k++) {
				const dt = k / sampleRate;
				data[start + k] += Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * decay) * amp;
			}
		}
	}

	for (let i = 0; i < length; i++) {
		if (data[i] > 1) data[i] = 1;
		else if (data[i] < -1) data[i] = -1;
	}
}

/**
 * Synthesizes a clean ~0.48s analog stylus needle drop (tip contact click, damped
 * tonearm thump, brief groove-catch scratch, and sparse vinyl ticks) with zero static hiss.
 */
export function synthesizeNeedleDrop(
	data: Float32Array,
	sampleRate: number,
	rng: () => number = Math.random
) {
	data.fill(0);
	const length = data.length;
	if (length === 0 || sampleRate <= 0) return;

	// 1. Stylus tip contact click + warm tonearm body resonance thump at t = 12ms
	const contactStart = Math.min(length - 1, Math.floor(sampleRate * 0.012));
	const thumpLen = Math.min(length - contactStart, Math.ceil(sampleRate * 0.075));
	for (let k = 0; k < thumpLen; k++) {
		const dt = k / sampleRate;
		const freq = Math.max(36, 68 - dt * 320);
		const thump = Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * 42) * 0.55;
		const click = dt < 0.0025 ? Math.sin(2 * Math.PI * 2900 * dt) * Math.exp(-dt * 1800) * 0.45 : 0;
		data[contactStart + k] += thump + click;
	}

	// 2. Brief groove-wall catch micro-scratch (t = 40ms..66ms, discrete stylus impulses)
	const scratchImpulses = 7;
	for (let s = 0; s < scratchImpulses; s++) {
		const onsetSec = 0.04 + s * 0.0034 + rng() * 0.001;
		const start = Math.floor(onsetSec * sampleRate);
		const env = Math.sin(((s + 0.5) / scratchImpulses) * Math.PI);
		const freq = 1700 + rng() * 1500;
		const amp = (0.22 + rng() * 0.14) * env * (s % 2 === 0 ? 1 : -1);
		const grainLen = Math.ceil(sampleRate * 0.0018);
		for (let k = 0; k < grainLen && start + k < length; k++) {
			const dt = k / sampleRate;
			data[start + k] += Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * 2100) * amp;
		}
	}

	// 3. Lead-in groove dust ticks (4 sparse vinyl ticks before audio begins)
	const leadInTicks = [0.12, 0.21, 0.31, 0.4];
	for (const sec of leadInTicks) {
		const start = Math.floor((sec + rng() * 0.02) * sampleRate);
		const freq = 900 + rng() * 2400;
		const decay = 1600 + rng() * 1800;
		const amp = (0.2 + rng() * 0.25) * (rng() < 0.5 ? -1 : 1);
		const tickLen = Math.ceil(sampleRate * 0.0025);
		for (let k = 0; k < tickLen && start + k < length; k++) {
			const dt = k / sampleRate;
			data[start + k] += Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt * decay) * amp;
		}
	}

	for (let i = 0; i < length; i++) {
		if (data[i] > 1) data[i] = 1;
		else if (data[i] < -1) data[i] = -1;
	}
}
