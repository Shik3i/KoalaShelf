import { describe, expect, it } from 'vitest';
import { needsGraph, NO_SOUND } from './sound.ts';

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
