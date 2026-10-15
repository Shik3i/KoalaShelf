// Sound processing through Web Audio: volume boost, mono, clearer voices, volume leveling
// and silence detection (for skipping silence).
//
// The <audio> element is routed through Web Audio once (createMediaElementSource cannot be
// undone); switching every effect off afterwards leaves a plain pass-through.

export interface SoundSettings {
	/** Even out loud and quiet passages (compressor). */
	leveling: boolean;
	/** Output gain: 1 = normal, up to 2 = twice as loud. */
	boost: number;
	/** Presence lift and rumble cut for speech. */
	voice: boolean;
	/** Both channels mixed into one (one earbud, uneven stereo). */
	mono: boolean;
	/** Report silent stretches (the player then speeds through them). */
	silence: boolean;
}

export const NO_SOUND: SoundSettings = {
	leveling: false,
	boost: 1,
	voice: false,
	mono: false,
	silence: false
};

/** The compressor lowers peaks; this brings the overall level back up. */
const MAKEUP_GAIN = 1.6;
const WORKLET = '/worklets/silence.js';

/** True when any setting needs the Web Audio graph. */
export function needsGraph(s: SoundSettings) {
	return s.leveling || s.boost !== 1 || s.voice || s.mono || s.silence;
}

export class SoundChain {
	#ctx: AudioContext | null = null;
	#source: MediaElementAudioSourceNode | null = null;
	#mono: GainNode | null = null;
	#highpass: BiquadFilterNode | null = null;
	#presence: BiquadFilterNode | null = null;
	#compressor: DynamicsCompressorNode | null = null;
	#gain: GainNode | null = null;
	#detector: AudioWorkletNode | null = null;
	#detectorLoading: Promise<void> | null = null;
	#settings: SoundSettings = NO_SOUND;
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
		this.#compressor = c;
		this.#gain = ctx.createGain();
		this.#route();
	}

	set(settings: SoundSettings) {
		this.#settings = { ...settings };
		this.#route();
		if (settings.silence) void this.#loadDetector();
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

	#route() {
		const ctx = this.#ctx;
		const src = this.#source;
		const gain = this.#gain;
		if (!ctx || !src || !gain) return;
		const s = this.#settings;
		const nodes = [
			this.#mono,
			this.#highpass,
			this.#presence,
			this.#compressor,
			gain,
			this.#detector
		];
		src.disconnect();
		for (const n of nodes) n?.disconnect();
		const chain: AudioNode[] = [];
		if (s.mono) chain.push(this.#mono!);
		if (s.voice) chain.push(this.#highpass!, this.#presence!);
		if (s.leveling) chain.push(this.#compressor!);
		chain.push(gain);
		gain.gain.value = (s.leveling ? MAKEUP_GAIN : 1) * s.boost;
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
