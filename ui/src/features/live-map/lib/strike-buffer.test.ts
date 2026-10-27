import { describe, expect, it } from 'vitest';

import type { StrikeResponse } from '@api/models/strike-response';

import { passesMds, StrikeBuffer } from './strike-buffer';

const T0 = Date.parse('2026-10-18T12:00:00Z');
const strike = (secondsAgo: number, lat = 50, mds = 5000): StrikeResponse => ({
  time: new Date(T0 - secondsAgo * 1000).toISOString(),
  lat,
  lon: 10,
  region: 1,
  stations: 20,
  delaySeconds: 5,
  mds,
});

describe('StrikeBuffer', () => {
  it('ignores the same strike arriving twice', () => {
    const buffer = new StrikeBuffer(15 * 60_000);
    buffer.add(strike(10));
    buffer.add(strike(10));
    expect(buffer.size).toBe(1);
  });

  it('maps age from 0 (now) to 1 (window edge)', () => {
    const buffer = new StrikeBuffer(100_000);
    buffer.add(strike(0, 1));
    buffer.add(strike(50, 2));
    const ages = buffer.snapshot(T0).features.map((f) => f.properties.age);
    expect(ages).toEqual([0, 0.5]);
  });

  it('evicts strikes older than the window', () => {
    const buffer = new StrikeBuffer(60_000);
    buffer.add(strike(30));
    buffer.add(strike(90));
    expect(buffer.snapshot(T0).features).toHaveLength(1);
    expect(buffer.size).toBe(1);
  });

  it('puts coordinates in GeoJSON order: [lon, lat]', () => {
    const buffer = new StrikeBuffer(60_000);
    buffer.add(strike(1, 41.5));
    expect(buffer.snapshot(T0).features[0].geometry.coordinates).toEqual([10, 41.5]);
  });

  it('notifies arrival listeners only for new live strikes', () => {
    const buffer = new StrikeBuffer(15 * 60_000);
    const arrived: number[] = [];
    buffer.onArrival((s) => arrived.push(s.lat));
    buffer.add(strike(20, 1), true); // live
    buffer.add(strike(25, 2)); // backfill from /strikes/recent
    buffer.add(strike(20, 1), true); // repeat
    expect(arrived).toEqual([1]);
  });

  it('leaves out strikes above maxMds and keeps them for an unfiltered snapshot', () => {
    const buffer = new StrikeBuffer(60_000);
    buffer.add(strike(1, 1, 8_000));
    buffer.add(strike(1, 2, 12_000));
    const lats = (maxMds: number | null) => buffer.snapshot(T0, maxMds).features.map((f) => f.geometry.coordinates[1]);
    expect(lats(10_000)).toEqual([1]);
    expect(lats(null)).toEqual([1, 2]);
  });

  it('still evicts old strikes that the filter hides', () => {
    const buffer = new StrikeBuffer(60_000);
    buffer.add(strike(90, 1, 12_000));
    buffer.snapshot(T0, 10_000);
    expect(buffer.size).toBe(0);
  });
});

describe('passesMds', () => {
  it('passes everything without a limit', () => {
    expect(passesMds(0, null)).toBe(true);
  });

  it('keeps strikes up to and including the limit', () => {
    expect(passesMds(10_000, 10_000)).toBe(true);
    expect(passesMds(10_001, 10_000)).toBe(false);
  });

  it('treats a missing mds (0) as not passing a limit', () => {
    expect(passesMds(0, 10_000)).toBe(false);
  });
});
