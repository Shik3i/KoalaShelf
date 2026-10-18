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
		if (this.#ctx) return;
		const ctx = new AudioContext({ latencyHint: 'playback' });
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
		this.#ctx = ctx;
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

	/** Plays a subtle authentic 1.2s vintage stylus needle drop sound cue. */
	playNeedleDrop() {
		const ctx = this.#ctx;
		if (!ctx || ctx.state === 'suspended') return;
		try {
			const now = ctx.currentTime;
			const duration = 1.2;
			const buffer = ctx.createBuffer(1, Math.floor(ctx.sampleRate * duration), ctx.sampleRate);
			const data = buffer.getChannelData(0);
			for (let i = 0; i < data.length; i++) {
				const t = i / ctx.sampleRate;
				const thud = t < 0.08 ? Math.sin(2 * Math.PI * 65 * t) * Math.exp(-t * 40) * 0.4 : 0;
				const scratch =
					t >= 0.04 && t < 0.3 ? (Math.random() * 2 - 1) * Math.exp(-(t - 0.04) * 12) * 0.25 : 0;
				const hiss =
					t >= 0.2 ? (Math.random() * 2 - 1) * 0.08 * (1 - (t - 0.2) / 1.0) : 0;
				const pop = Math.random() < 8 / ctx.sampleRate ? (Math.random() - 0.5) * 0.3 : 0;
				data[i] = thud + scratch + hiss + pop;
			}
			const source = ctx.createBufferSource();
			source.buffer = buffer;
			const filter = ctx.createBiquadFilter();
			filter.type = 'lowpass';
			filter.frequency.value = 3500;
			const gain = ctx.createGain();
			gain.gain.setValueAtTime(0.18, now);
			source.connect(filter);
			filter.connect(gain);
			gain.connect(ctx.destination);
			source.start(now);
		} catch {
			// Ignore if buffer cannot be scheduled
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
				const length = sampleRate * 4;
				const buffer = ctx.createBuffer(1, length, sampleRate);
				const data = buffer.getChannelData(0);
				let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
				for (let i = 0; i < length; i++) {
					const white = Math.random() * 2 - 1;
					b0 = 0.99886 * b0 + white * 0.0555179;
					b1 = 0.99332 * b1 + white * 0.0750759;
					b2 = 0.96900 * b2 + white * 0.1538520;
					b3 = 0.86650 * b3 + white * 0.3104856;
					b4 = 0.55000 * b4 + white * 0.5329522;
					b5 = -0.76160 * b5 - white * 0.0168980;
					const pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.06;
					b6 = white * 0.115926;
					let sample = pink;
					if (Math.random() < 14 / sampleRate) {
						sample += (Math.random() * 0.7 + 0.3) * (Math.random() > 0.5 ? 1 : -1);
					}
					data[i] = sample;
				}

				const src = ctx.createBufferSource();
				src.buffer = buffer;
				src.loop = true;

				const filter = ctx.createBiquadFilter();
				filter.type = 'bandpass';
				filter.frequency.value = 1800;
				filter.Q.value = 0.6;

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
