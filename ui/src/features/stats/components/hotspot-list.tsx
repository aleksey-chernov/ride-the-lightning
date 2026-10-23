import type { Hotspot } from '@api/models/hotspot';
import { formatCoordinates, formatCount } from '@features/stats/utils/stats-format.utils';

type Props = { hotspots: Hotspot[]; onSelect: (hotspot: { lon: number; lat: number }) => void };

/** Busiest 1° cells in the last 10 minutes; picking one asks to fly the map there. */
export function HotspotList({ hotspots, onSelect }: Props) {
  return (
    <section className="space-y-2" aria-label="Hotspots, last 10 minutes">
      <h3 className="text-xs font-medium text-muted-foreground">Hotspots, last 10 min</h3>
      {hotspots.length === 0 ? (
        <p className="text-xs text-muted-foreground">No hotspots in the last 10 minutes</p>
      ) : (
        <ul className="-mx-2">
          {hotspots.map(({ lat, lon, place, count }) => (
            <li key={`${lat},${lon}`}>
              <button
                type="button"
                className="flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset"
                aria-label={`Show ${place}, ${formatCoordinates(lat, lon)}, on the map, ${formatCount(count)} ${count === 1 ? 'strike' : 'strikes'}`}
                onClick={() => onSelect({ lon, lat })}
              >
                <span className="min-w-0">
                  <span className="block truncate">{place}</span>
                  <span className="block text-xs text-muted-foreground tabular-nums">{formatCoordinates(lat, lon)}</span>
                </span>
                <span className="shrink-0 text-muted-foreground tabular-nums">{formatCount(count)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
