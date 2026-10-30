import { describe, expect, it } from 'vitest';

import type { PlaybackStrikeResponse } from '@api/models/playback-strike-response';

import { MAX_FRAME_FEATURES, StrikeTimeline } from './strike-timeline';

const T0 = Date.parse('2026-10-29T12:00:00Z');
const TRAIL = 60_000;
const strike = (secondsAfterT0: number, lat = 50): PlaybackStrikeResponse => ({ timeMs: T0 + secondsAfterT0 * 1000, lat, lon: 10 });
const lats = (timeline: StrikeTimeline, atMs: number) => timeline.snapshot(atMs, TRAIL).features.map((f) => f.geometry.coordinates[1]);

describe('StrikeTimeline', () => {
  it('shows strikes from the trail end up to and including the playhead', () => {
    const timeline = new StrikeTimeline([strike(-61, 1), strike(-60, 2), strike(-1, 3), strike(0, 4), strike(1, 5)]);
    expect(lats(timeline, T0)).toEqual([2, 3, 4]);
  });

  it('leaves out a strike just before the trail end', () => {
    const timeline = new StrikeTimeline([{ timeMs: T0 - TRAIL - 1, lat: 1, lon: 10 }]);
    expect(lats(timeline, T0)).toEqual([]);
  });

  it('maps age from 0 (playhead) to 1 (trail end)', () => {
    const timeline = new StrikeTimeline([strike(-60), strike(-30), strike(0)]);
    expect(timeline.snapshot(T0, TRAIL).features.map((f) => f.properties.age)).toEqual([1, 0.5, 0]);
  });

  it('shows earlier strikes again when scrubbing backwards', () => {
    const timeline = new StrikeTimeline([strike(0, 1), strike(600, 2)]);
    expect(lats(timeline, T0 + 600_000)).toEqual([2]);
    expect(lats(timeline, T0 + 30_000)).toEqual([1]);
  });

  it('gives an empty collection without strikes', () => {
    expect(new StrikeTimeline([]).snapshot(T0, TRAIL).features).toEqual([]);
  });

  it('thins a busy frame to at most MAX_FRAME_FEATURES, spread over the whole slice', () => {
    const strikes = Array.from({ length: 50_000 }, (_, i) => ({ timeMs: T0 - TRAIL + i, lat: i, lon: 10 }));
    const features = new StrikeTimeline(strikes).snapshot(T0, TRAIL).features;
    expect(features.length).toBeLessThanOrEqual(MAX_FRAME_FEATURES);
    expect(features.length).toBeGreaterThan(MAX_FRAME_FEATURES * 0.9);
    expect(features.at(-1)?.geometry.coordinates[1]).toBeGreaterThan(49_000);
  });

  it('keeps showing the same strikes of a thinned slice as the playhead moves', () => {
    const strikes = Array.from({ length: 50_000 }, (_, i) => ({ timeMs: T0 - TRAIL + i, lat: i, lon: 10 }));
    const timeline = new StrikeTimeline(strikes);
    const at = (ms: number) => new Set(timeline.snapshot(ms, TRAIL).features.map((f) => f.geometry.coordinates[1]));
    const before = at(T0);
    const after = at(T0 + 3);
    expect([...after].filter((lat) => lat < 49_990).every((lat) => before.has(lat))).toBe(true);
  });

  it('lists the strikes the playhead passed, excluding the start and including the end', () => {
    const timeline = new StrikeTimeline([strike(0, 1), strike(1, 2), strike(2, 3), strike(3, 4)]);
    expect(timeline.arrivals(T0, T0 + 2000, 10).map((s) => s.lat)).toEqual([2, 3]);
    expect(timeline.arrivals(T0 + 3000, T0 + 9000, 10)).toEqual([]);
  });

  it('caps arrivals, spread over the passed interval', () => {
    const timeline = new StrikeTimeline(Array.from({ length: 100 }, (_, i) => strike(i, i)));
    expect(timeline.arrivals(T0 - 1, T0 + 99_000, 4).map((s) => s.lat)).toEqual([12, 37, 62, 87]);
  });
});
