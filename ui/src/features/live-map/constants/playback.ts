const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;

export type PresetId = '1h' | '6h' | '24h' | '7d';
export type SpeedId = '1m' | '10m' | '1h';

/** Ranges ending now; `sliderStepMs` keeps the slider at roughly 300 positions or fewer. */
export const PRESETS: readonly { id: PresetId; label: string; ariaLabel: string; spanMs: number; sliderStepMs: number }[] = [
  { id: '1h', label: '1h', ariaLabel: 'Last hour', spanMs: HOUR_MS, sliderStepMs: MINUTE_MS },
  { id: '6h', label: '6h', ariaLabel: 'Last 6 hours', spanMs: 6 * HOUR_MS, sliderStepMs: MINUTE_MS },
  { id: '24h', label: '24h', ariaLabel: 'Last 24 hours', spanMs: 24 * HOUR_MS, sliderStepMs: 5 * MINUTE_MS },
  { id: '7d', label: '7d', ariaLabel: 'Last 7 days', spanMs: 7 * 24 * HOUR_MS, sliderStepMs: 30 * MINUTE_MS },
];

/** Playback time per real second, and how long a strike stays visible while it fades (`age` 0 → 1). */
export const SPEEDS: readonly { id: SpeedId; label: string; ariaLabel: string; factor: number; trailMs: number }[] = [
  { id: '1m', label: '1 min/s', ariaLabel: '1 minute per second', factor: 60, trailMs: 15 * MINUTE_MS },
  { id: '10m', label: '10 min/s', ariaLabel: '10 minutes per second', factor: 600, trailMs: HOUR_MS },
  { id: '1h', label: '1 h/s', ariaLabel: '1 hour per second', factor: 3600, trailMs: 3 * HOUR_MS },
];

export const DEFAULT_PRESET: PresetId = '6h';
export const DEFAULT_SPEED: SpeedId = '10m';

/** The only playback clock: one store tick, and so one map redraw, per interval (D39: setData ~30×/s starved the basemap). */
export const PLAYBACK_TICK_MS = 250;
/** Flashes per playback frame at most (so ≤ 16/s): at 60-3600× a frame can pass hundreds of strikes. */
export const MAX_FLASHES_PER_FRAME = 4;
/** Background tabs fire intervals late (up to once a minute); a longer gap counts as this much. */
export const MAX_TICK_DELTA_MS = 1_000;
