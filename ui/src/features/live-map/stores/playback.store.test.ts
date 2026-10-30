import { beforeEach, describe, expect, it } from 'vitest';

import { selectMapMode, usePlaybackStore } from './playback.store';

const NOW = Date.parse('2026-10-29T12:00:00Z');
const HOUR = 3_600_000;
const initial = usePlaybackStore.getState();
const state = () => usePlaybackStore.getState();

describe('usePlaybackStore', () => {
  beforeEach(() => usePlaybackStore.setState(initial, true));

  it('starts live', () => {
    expect(selectMapMode(state())).toBe('live');
  });

  it('start loads the preset range and plays it from the beginning', () => {
    state().start('6h', NOW);
    expect(state().range).toEqual({ fromMs: NOW - 6 * HOUR, toMs: NOW });
    expect(state().positionMs).toBe(NOW - 6 * HOUR);
    expect(state().playing).toBe(true);
    expect(selectMapMode(state())).toBe('playback');
  });

  it('start begins no earlier than the oldest stored strike', () => {
    state().start('24h', NOW, NOW - 2 * HOUR);
    expect(state().range?.fromMs).toBe(NOW - 2 * HOUR);
  });

  it('tick advances by real time × speed', () => {
    state().start('1h', NOW);
    state().tick(250); // 10 min/s = 600×
    expect(state().positionMs).toBe(NOW - HOUR + 150_000);
  });

  it('tick counts a long gap (a throttled background tab) as at most a second', () => {
    state().start('6h', NOW);
    state().tick(60_000);
    expect(state().positionMs).toBe(NOW - 6 * HOUR + 600_000);
  });

  it('tick stops at the end and pauses', () => {
    state().start('1h', NOW);
    state().setSpeed('1h');
    state().tick(1_000);
    state().tick(1_000);
    expect(state().positionMs).toBe(NOW);
    expect(state().playing).toBe(false);
  });

  it('tick does nothing while paused', () => {
    state().start('1h', NOW);
    state().pause();
    state().tick(250);
    expect(state().positionMs).toBe(NOW - HOUR);
  });

  it('play at the end starts over', () => {
    state().start('1h', NOW);
    state().seek(NOW);
    state().pause();
    state().play();
    expect(state().positionMs).toBe(NOW - HOUR);
    expect(state().playing).toBe(true);
  });

  it('seek stays inside the range and keeps playing', () => {
    state().start('1h', NOW);
    state().seek(NOW + HOUR);
    expect(state().positionMs).toBe(NOW);
    state().seek(0);
    expect(state().positionMs).toBe(NOW - HOUR);
    expect(state().playing).toBe(true);
  });

  it('stop goes back to live', () => {
    state().start('1h', NOW);
    state().stop();
    expect(selectMapMode(state())).toBe('live');
    expect(state().playing).toBe(false);
  });
});
