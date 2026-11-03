import { describe, expect, it } from 'vitest';
import {
	needsGraph,
	NO_SOUND,
	SoundChain,
	synthesizeNeedleDrop,
	synthesizeVinylCrackle
} from './sound.ts';

describe('SoundChain needsGraph', () => {
	it('returns false for default no-sound configuration', () => {
		expect(needsGraph(NO_SOUND)).toBe(false);
	});

	it('returns true when boost is not 1', () => {
		expect(needsGraph({ ...NO_SOUND, boost: 1.5 })).toBe(true);
	});

	it('returns true when leveling is active', () => {
		expect(needsGraph({ ...NO_SOUND, leveling: true })).toBe(true);
	});

	it('returns true when soundProfile is set to a scene', () => {
		expect(needsGraph({ ...NO_SOUND, profile: 'voice' })).toBe(true);
		expect(needsGraph({ ...NO_SOUND, profile: 'night' })).toBe(true);
		expect(needsGraph({ ...NO_SOUND, profile: 'commute' })).toBe(true);
		expect(needsGraph({ ...NO_SOUND, profile: 'off' })).toBe(false);
	});

	it('returns true when vinyl crackle is active', () => {
		expect(needsGraph({ ...NO_SOUND, crackle: true })).toBe(true);
	});

	it('returns true when mono or silence is enabled', () => {
		expect(needsGraph({ ...NO_SOUND, mono: true })).toBe(true);
		expect(needsGraph({ ...NO_SOUND, silence: true })).toBe(true);
	});
});

describe('SoundChain instance', () => {
	it('instantiates and provides initial state', () => {
		const chain = new SoundChain();
		expect(chain.attached).toBe(false);
		expect(chain.output).toBe(null);
	});

	it('safely handles playNeedleDrop without throwing', async () => {
		const chain = new SoundChain();
		await expect(chain.playNeedleDrop()).resolves.not.toThrow();
	});

	it('synthesizes clean vinyl crackle transients with zero continuous static noise floor', () => {
		const sampleRate = 44100;
		const data = new Float32Array(sampleRate * 4);
		let seed = 42;
		const rng = () => {
			seed = (seed * 16807) % 2147483647;
			return (seed - 1) / 2147483646;
		};
		synthesizeVinylCrackle(data, sampleRate, rng);

		let silentSamples = 0;
		let peak = 0;
		let sumSq = 0;
		for (let i = 0; i < data.length; i++) {
			const abs = Math.abs(data[i]);
			if (abs < 1e-4) silentSamples++;
			if (abs > peak) peak = abs;
			sumSq += data[i] * data[i];
		}
		const silenceRatio = silentSamples / data.length;
		const rms = Math.sqrt(sumSq / data.length);

		// Must contain audible vinyl pops/ticks (peak > 0.35), while >90% of the buffer
		// is completely silent between transients (no continuous pink/white noise hiss!).
		expect(peak).toBeGreaterThan(0.35);
		expect(peak).toBeLessThanOrEqual(1);
		expect(silenceRatio).toBeGreaterThan(0.9);
		expect(rms).toBeLessThan(0.05);
	});

	it('synthesizes needle drop with tonearm thump and groove catch without static hiss tail', () => {
		const sampleRate = 44100;
		const data = new Float32Array(Math.floor(sampleRate * 0.48));
		let seed = 99;
		const rng = () => {
			seed = (seed * 16807) % 2147483647;
			return (seed - 1) / 2147483646;
		};
		synthesizeNeedleDrop(data, sampleRate, rng);

		let silentSamples = 0;
		let peak = 0;
		for (let i = 0; i < data.length; i++) {
			const abs = Math.abs(data[i]);
			if (abs < 1e-4) silentSamples++;
			if (abs > peak) peak = abs;
		}
		// Distinct contact thump/click and mostly silent background (no 1-second white noise hiss).
		expect(peak).toBeGreaterThan(0.4);
		expect(silentSamples / data.length).toBeGreaterThan(0.75);
	});
});
