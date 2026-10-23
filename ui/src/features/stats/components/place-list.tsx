import { Waves } from 'lucide-react';

import { formatCount, type PlaceShare } from '@features/stats/utils/stats-format.utils';

type Props = { places: PlaceShare[] };

/** Busiest countries and seas in the last 10 minutes, with a bar relative to the busiest. */
export function PlaceList({ places }: Props) {
  return (
    <section className="space-y-2" aria-label="Top places, last 10 minutes">
      <h3 className="text-xs font-medium text-muted-foreground">Top places, last 10 min</h3>
      {places.length === 0 ? (
        <p className="text-xs text-muted-foreground">No strikes in the last 10 minutes</p>
      ) : (
        <ul className="space-y-2">
          {places.map(({ name, kind, count, share }) => (
            <li key={`${kind}:${name}`} className="space-y-1">
              <div className="flex items-center justify-between gap-2">
                <span className="flex min-w-0 items-center gap-1.5">
                  {kind === 'sea' && <Waves aria-hidden className="size-3 shrink-0 text-muted-foreground" />}
                  <span className="truncate" title={name}>
                    {name}
                  </span>
                  {kind === 'sea' && <span className="sr-only">(sea)</span>}
                </span>
                <span className="shrink-0 text-muted-foreground tabular-nums">{formatCount(count)}</span>
              </div>
              <div className="h-1 rounded-full bg-muted">
                <div className="h-full rounded-full bg-chart-1" style={{ width: `${share * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
