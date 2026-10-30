import type { FeatureCollection, Point } from 'geojson';

import type { StrikeResponse } from '@api/models/strike-response';

type BufferedStrike = { timeMs: number; lat: number; lon: number };

export type StrikeFeatures = FeatureCollection<Point, { age: number }>;
/** A strike to flash: new over the stream (live) or passed by the playhead (playback). */
export type ArrivalListener = (s: { lon: number; lat: number }) => void;

/** Recent strikes, kept outside React state; the live map reads snapshots on a timer. */
export class StrikeBuffer {
  readonly #strikes = new Map<string, BufferedStrike>();
  readonly #listeners = new Set<ArrivalListener>();
  readonly #windowMs: number;

  constructor(windowMs: number) {
    this.#windowMs = windowMs;
  }

  get size(): number {
    return this.#strikes.size;
  }

  /** Ignores repeats, e.g. when /strikes/recent and the stream overlap. `live` strikes notify arrival listeners. */
  add(s: StrikeResponse, live = false): void {
    const key = `${s.time}|${s.lat}|${s.lon}`;
    if (this.#strikes.has(key)) return;
    this.#strikes.set(key, { timeMs: Date.parse(s.time), lat: s.lat, lon: s.lon });
    if (live) this.#listeners.forEach((listener) => listener(s));
  }

  /** Called for each new strike from the live stream; returns an unsubscribe function. */
  onArrival(listener: ArrivalListener): () => void {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  }

  /** Evicts strikes older than the window; also runs on a timer, so the buffer stays small while the map shows playback. */
  prune(nowMs: number): void {
    for (const [key, s] of this.#strikes) if (nowMs - s.timeMs > this.#windowMs) this.#strikes.delete(key);
  }

  /** Evicts, then maps `age` from 0 (now) to 1 (oldest kept). */
  snapshot(nowMs: number): StrikeFeatures {
    this.prune(nowMs);
    const features: StrikeFeatures['features'] = [];
    for (const s of this.#strikes.values()) {
      const age = (nowMs - s.timeMs) / this.#windowMs;
      features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [s.lon, s.lat] }, properties: { age: Math.max(0, age) } });
    }
    return { type: 'FeatureCollection', features };
  }
}
