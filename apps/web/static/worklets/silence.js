// Silence detector (audio thread). Posts true when a silent stretch begins and false as
// soon as sound returns, so the player can speed through pauses without polling.
const THRESHOLD_DB = -44;
/** Silence shorter than this is part of the narration's rhythm and is kept. */
const MIN_SILENCE_S = 0.4;

class SilenceDetector extends AudioWorkletProcessor {
	quiet = 0;
	silent = false;

	process(inputs) {
		const channels = inputs[0];
		if (!channels || !channels.length) return true;
		let sum = 0;
		let n = 0;
		for (const c of channels) {
			for (let i = 0; i < c.length; i++) sum += c[i] * c[i];
			n += c.length;
		}
		const db = 20 * Math.log10(Math.sqrt(sum / Math.max(1, n)) + 1e-9);
		if (db < THRESHOLD_DB) {
			this.quiet += channels[0].length / sampleRate;
			if (!this.silent && this.quiet >= MIN_SILENCE_S) {
				this.silent = true;
				this.port.postMessage(true);
			}
		} else {
			this.quiet = 0;
			if (this.silent) {
				this.silent = false;
				this.port.postMessage(false);
			}
		}
		return true;
	}
}

registerProcessor('ks-silence', SilenceDetector);
