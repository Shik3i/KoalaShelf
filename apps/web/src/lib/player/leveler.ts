// Volume leveling: a gentle compressor that lifts quiet passages and tames loud ones,
// so whispered dialogue and shouted scenes need no reaching for the volume.
//
// The <audio> element is routed through Web Audio once (createMediaElementSource cannot be
// undone); turning leveling off afterwards bypasses the compressor instead.

/** The compressor lowers peaks; this brings the overall level back up. */
const MAKEUP_GAIN = 1.6;

export class Leveler {
	#ctx: AudioContext | null = null;
	#source: MediaElementAudioSourceNode | null = null;
	#compressor: DynamicsCompressorNode | null = null;
	#gain: GainNode | null = null;
	#on = false;

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

	attach(audio: HTMLAudioElement) {
		if (this.#ctx) return;
		const ctx = new AudioContext({ latencyHint: 'playback' });
		const c = ctx.createDynamicsCompressor();
		// Speech-friendly: start compressing well below peaks, soft knee, moderate ratio.
		c.threshold.value = -32;
		c.knee.value = 12;
		c.ratio.value = 5;
		c.attack.value = 0.005;
		c.release.value = 0.3;
		const gain = ctx.createGain();
		this.#ctx = ctx;
		this.#source = ctx.createMediaElementSource(audio);
		this.#compressor = c;
		this.#gain = gain;
		this.#route();
	}

	set(on: boolean) {
		this.#on = on;
		this.#route();
	}

	/** Browsers start audio contexts suspended until a user gesture; call before play(). */
	async resume() {
		if (this.#ctx?.state === 'suspended') await this.#ctx.resume().catch(() => undefined);
	}

	#route() {
		const ctx = this.#ctx;
		const [src, c, gain] = [this.#source, this.#compressor, this.#gain];
		if (!ctx || !src || !c || !gain) return;
		src.disconnect();
		c.disconnect();
		gain.disconnect();
		// Off: bypass the compressor, gain at unity (the graph end stays the same for meters).
		gain.gain.value = this.#on ? MAKEUP_GAIN : 1;
		if (this.#on) src.connect(c).connect(gain);
		else src.connect(gain);
		gain.connect(ctx.destination);
	}
}
