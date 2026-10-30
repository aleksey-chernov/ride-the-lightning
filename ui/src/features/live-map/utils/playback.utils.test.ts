import { describe, expect, it } from 'vitest';

import { formatPlayhead, isPresetAvailable, rangeFor, startPreset } from './playback.utils';

const NOW = Date.parse('2026-10-27T14:32:00Z'); // a Tuesday
const HOUR = 3_600_000;
const UTC = { locale: 'en-GB', timeZone: 'UTC' };

describe('rangeFor', () => {
  it('ends now and spans the preset', () => {
    expect(rangeFor('6h', NOW)).toEqual({ fromMs: NOW - 6 * HOUR, toMs: NOW });
  });

  it('starts at the oldest stored strike when that is later', () => {
    expect(rangeFor('24h', NOW, NOW - 3 * HOUR)).toEqual({ fromMs: NOW - 3 * HOUR, toMs: NOW });
  });

  it('keeps the preset start when history goes back further', () => {
    expect(rangeFor('1h', NOW, NOW - 3 * HOUR)).toEqual({ fromMs: NOW - HOUR, toMs: NOW });
  });
});

describe('isPresetAvailable', () => {
  it('offers the shortest preset that covers everything stored, and all shorter ones', () => {
    const offered = (storedSpanMs: number) => (['1h', '6h', '24h', '7d'] as const).filter((p) => isPresetAvailable(p, storedSpanMs));
    expect(offered(0)).toEqual(['1h']);
    expect(offered(3 * HOUR)).toEqual(['1h', '6h']);
    expect(offered(30 * HOUR)).toEqual(['1h', '6h', '24h', '7d']);
  });
});

describe('startPreset', () => {
  it('is 6 h when that much is stored or more', () => {
    expect(startPreset(48 * HOUR)).toBe('6h');
  });

  it('is 1 h for a short history', () => {
    expect(startPreset(10 * 60_000)).toBe('1h');
  });
});

describe('formatPlayhead', () => {
  it('shows the 24-hour time for ranges under a day', () => {
    expect(formatPlayhead(NOW, 6 * HOUR, UTC)).toBe('14:32');
  });

  it('adds the weekday from a day on', () => {
    expect(formatPlayhead(NOW, 24 * HOUR, UTC)).toBe('Tue 14:32');
  });

  it('uses 24-hour time even where the locale prefers AM/PM', () => {
    expect(formatPlayhead(NOW, HOUR, { locale: 'en-US', timeZone: 'UTC' })).toBe('14:32');
  });
});
