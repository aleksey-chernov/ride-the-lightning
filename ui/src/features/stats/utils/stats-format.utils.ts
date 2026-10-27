import type { MinuteCount } from '@api/models/minute-count';
import type { PlaceCount } from '@api/models/place-count';
import type { PlaceCountKind } from '@api/models/place-count-kind';
import type { StatsResponse } from '@api/models/stats-response';
import { formatMicros } from '@shared/utils/units.utils';

export type MinutePoint = { minute: string; label: string; count: number };

export type PlaceShare = { name: string; kind: PlaceCountKind; count: number; share: number };

const COUNT_FORMAT = new Intl.NumberFormat('en-US');

export function formatCount(count: number): string {
  return COUNT_FORMAT.format(count);
}

function formatDegrees(value: number, positive: string, negative: string): string {
  const degrees = `${Math.abs(value).toFixed(2)}°`;
  if (value === 0) return degrees;
  return degrees + (value > 0 ? positive : negative);
}

/** "41.75°N 2.25°E"; the equator and prime meridian get no hemisphere letter. */
export function formatCoordinates(lat: number, lon: number): string {
  return `${formatDegrees(lat, 'N', 'S')} ${formatDegrees(lon, 'E', 'W')}`;
}

/** Share of received messages that were duplicates, as "12.3"; "0.0" when nothing arrived yet. */
export function formatDuplicateRate(received: number, duplicatesDropped: number): string {
  const rate = received > 0 ? (duplicatesDropped / received) * 100 : 0;
  return rate.toFixed(1);
}

/** Local "HH:mm" for an ISO timestamp. */
export function formatLocalTime(iso: string): string {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

export function toMinutePoints(perMinute: MinuteCount[]): MinutePoint[] {
  return perMinute.map(({ minute, count }) => ({ minute, label: formatLocalTime(minute), count }));
}

/** Places with their count relative to the busiest one (0..1), for proportional bars. */
export function toPlaceShares(places: PlaceCount[]): PlaceShare[] {
  const max = Math.max(0, ...places.map((p) => p.count));
  return places.map((place) => ({ ...place, share: max > 0 ? place.count / max : 0 }));
}

/** Card subtitle: a backend error wins; otherwise the refresh note, plus the applied filter as echoed by the backend. */
export function describeStats(stats: Pick<StatsResponse, 'maxMds' | 'filteredOut'> | undefined, isError: boolean): string {
  if (isError) return stats ? 'Backend unreachable, showing last data' : 'Backend unreachable';
  const refreshed = 'Refreshed every 5 s';
  if (stats?.maxMds === undefined) return refreshed;
  return `${refreshed} · timing spread ≤ ${formatMicros(stats.maxMds)} · ${formatCount(stats.filteredOut)} hidden (last hour)`;
}
