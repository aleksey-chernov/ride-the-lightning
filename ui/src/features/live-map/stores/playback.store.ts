import { create } from 'zustand';

import { DEFAULT_PRESET, DEFAULT_SPEED, MAX_TICK_DELTA_MS, type PresetId, type SpeedId } from '@features/live-map/constants/playback';
import { rangeFor, speedOf, type PlaybackRange } from '@features/live-map/utils/playback.utils';

export type MapMode = 'live' | 'playback';

type PlaybackState = {
  /** null while the map is live */
  range: PlaybackRange | null;
  preset: PresetId;
  speed: SpeedId;
  positionMs: number;
  playing: boolean;
  /** Loads the preset's range ending at `nowMs` and plays it from the start. */
  start: (preset: PresetId, nowMs: number, oldestMs?: number) => void;
  stop: () => void;
  /** At the end, starts over. */
  play: () => void;
  pause: () => void;
  seek: (ms: number) => void;
  setSpeed: (speed: SpeedId) => void;
  /** Advances by `elapsedMs` of real time; pauses at the end. */
  tick: (elapsedMs: number) => void;
};

const clamp = (ms: number, { fromMs, toMs }: PlaybackRange) => Math.min(Math.max(ms, fromMs), toMs);

/** Map playback; not persisted, so the map always opens live. */
export const usePlaybackStore = create<PlaybackState>()((set) => ({
  range: null,
  preset: DEFAULT_PRESET,
  speed: DEFAULT_SPEED,
  positionMs: 0,
  playing: false,
  start: (preset, nowMs, oldestMs) => {
    const range = rangeFor(preset, nowMs, oldestMs);
    set({ range, preset, positionMs: range.fromMs, playing: true });
  },
  stop: () => set({ range: null, playing: false }),
  play: () => set((s) => (s.range ? { playing: true, positionMs: s.positionMs >= s.range.toMs ? s.range.fromMs : s.positionMs } : {})),
  pause: () => set({ playing: false }),
  seek: (ms) => set((s) => (s.range ? { positionMs: clamp(ms, s.range) } : {})),
  setSpeed: (speed) => set({ speed }),
  tick: (elapsedMs) =>
    set((s) => {
      if (!s.range || !s.playing) return {};
      const positionMs = clamp(s.positionMs + Math.min(elapsedMs, MAX_TICK_DELTA_MS) * speedOf(s.speed).factor, s.range);
      return { positionMs, playing: positionMs < s.range.toMs };
    }),
}));

export const selectMapMode = (s: PlaybackState): MapMode => (s.range ? 'playback' : 'live');
