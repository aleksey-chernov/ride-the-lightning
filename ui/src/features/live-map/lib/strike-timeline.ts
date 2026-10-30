import type { PlaybackStrikeResponse } from '@api/models/playback-strike-response';
import type { StrikeFeatures } from '@features/live-map/lib/strike-buffer';

/** Above this a frame shows every k-th strike, so MapLibre's worker isn't flooded (the server thins long ranges the same way). */
export const MAX_FRAME_FEATURES = 10_000;

/** Stored strikes for playback, oldest first; never evicts, so scrubbing backwards works. */
export class StrikeTimeline {
  readonly #strikes: readonly PlaybackStrikeResponse[];

  constructor(strikes: readonly PlaybackStrikeResponse[]) {
    this.#strikes = strikes;
  }

  get size(): number {
    return this.#strikes.length;
  }

  /** Strikes in `[atMs − trailMs, atMs]`; `age` goes from 0 (at the playhead) to 1 (trail end). */
  snapshot(atMs: number, trailMs: number): StrikeFeatures {
    const start = this.#firstIndex((t) => t >= atMs - trailMs);
    const end = this.#firstIndex((t) => t > atMs);
    const k = Math.max(1, Math.ceil((end - start) / MAX_FRAME_FEATURES));
    const features: StrikeFeatures['features'] = [];
    // aligned to absolute indices, so the same strikes stay picked while the playhead moves (no twinkling)
    for (let i = Math.ceil(start / k) * k; i < end; i += k) {
      const s = this.#strikes[i];
      features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [s.lon, s.lat] }, properties: { age: (atMs - s.timeMs) / trailMs } });
    }
    return { type: 'FeatureCollection', features };
  }

  /** Strikes in `(afterMs, untilMs]`, at most `max`, spread evenly over the interval when there are more. */
  arrivals(afterMs: number, untilMs: number, max: number): PlaybackStrikeResponse[] {
    const start = this.#firstIndex((t) => t > afterMs);
    const count = this.#firstIndex((t) => t > untilMs) - start;
    if (count <= max) return this.#strikes.slice(start, start + count);
    return Array.from({ length: max }, (_, i) => this.#strikes[start + Math.floor(((i + 0.5) * count) / max)]);
  }

  /** Binary search: the first index whose time satisfies `isAfter` (which holds from some index on), or the length. */
  #firstIndex(isAfter: (timeMs: number) => boolean): number {
    let lo = 0;
    let hi = this.#strikes.length;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (!isAfter(this.#strikes[mid].timeMs)) lo = mid + 1;
      else hi = mid;
    }
    return lo;
  }
}
