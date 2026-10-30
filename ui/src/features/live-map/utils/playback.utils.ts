import { DEFAULT_PRESET, PRESETS, SPEEDS, type PresetId, type SpeedId } from '@features/live-map/constants/playback';

const DAY_MS = 24 * 60 * 60_000;

export type PlaybackRange = { fromMs: number; toMs: number };

export function presetOf(id: PresetId): (typeof PRESETS)[number] {
  return PRESETS.find((p) => p.id === id) ?? PRESETS[0];
}

export function speedOf(id: SpeedId): (typeof SPEEDS)[number] {
  return SPEEDS.find((s) => s.id === id) ?? SPEEDS[0];
}

/** The preset's span ending at `nowMs`, starting no earlier than the oldest stored strike. */
export function rangeFor(preset: PresetId, nowMs: number, oldestMs?: number): PlaybackRange {
  const fromMs = nowMs - presetOf(preset).spanMs;
  return { fromMs: oldestMs === undefined ? fromMs : Math.min(Math.max(fromMs, oldestMs), nowMs), toMs: nowMs };
}

/** A preset is offered unless a shorter one already covers everything stored. */
export function isPresetAvailable(preset: PresetId, storedSpanMs: number): boolean {
  const index = PRESETS.findIndex((p) => p.id === preset);
  return index <= 0 || storedSpanMs > PRESETS[index - 1].spanMs;
}

/** The default preset, or the longest available one when history is shorter. */
export function startPreset(storedSpanMs: number): PresetId {
  const available = PRESETS.filter((p) => p.spanMs <= presetOf(DEFAULT_PRESET).spanMs && isPresetAvailable(p.id, storedSpanMs));
  return available.at(-1)?.id ?? PRESETS[0].id;
}

/** `14:32`, or `Tue 14:32` when the range spans a day or more (the 24 h range starts yesterday at the same clock time). */
export function formatPlayhead(ms: number, spanMs: number, { locale, timeZone }: { locale?: string; timeZone?: string } = {}): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: spanMs >= DAY_MS ? 'short' : undefined,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(ms);
}
