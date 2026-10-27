import type { Detection } from '@api/models/detection';
import type { Percentiles } from '@api/models/percentiles';
import { formatMicros, formatSeconds } from '@shared/utils/units.utils';
import { formatCount } from '@features/stats/utils/stats-format.utils';

import { StatTile } from './stat-tile';

type Props = { detection: Detection; maxMds: number | undefined };

function PercentileTiles({ title, percentiles, format }: { title: string; percentiles: Percentiles; format: (value: number) => string }) {
  return (
    <div className="space-y-1.5" role="group" aria-label={title}>
      <h4 className="text-xs text-muted-foreground">{title}</h4>
      <div className="grid grid-cols-3 gap-2">
        <StatTile label="Median" value={format(percentiles.p50)} />
        <StatTile label="p90" value={format(percentiles.p90)} />
        <StatTile label="p99" value={format(percentiles.p99)} />
      </div>
    </div>
  );
}

/** How strikes were detected in the last 10 minutes: publication delay, stations used and timing spread percentiles. */
export function DetectionSection({ detection: { delaySeconds, stationsUsed, mdsNanos }, maxMds }: Props) {
  return (
    <section className="space-y-3" aria-label="Detection, last 10 minutes">
      <h3 className="text-xs font-medium text-muted-foreground">Detection, last 10 min</h3>
      {!delaySeconds && !stationsUsed && !mdsNanos ? (
        <p className="text-xs text-muted-foreground">No strikes in the last 10 minutes</p>
      ) : (
        <>
          {delaySeconds && <PercentileTiles title="Publication delay" percentiles={delaySeconds} format={formatSeconds} />}
          {stationsUsed && <PercentileTiles title="Stations used" percentiles={stationsUsed} format={formatCount} />}
          <div className="space-y-1 text-xs text-muted-foreground">
            {stationsUsed && <p>Blitzortung lists at most 40 stations per strike, so high station counts are a lower bound.</p>}
            {mdsNanos && (
              <p className="tabular-nums">
                Timing spread: median {formatMicros(mdsNanos.p50)} · p90 {formatMicros(mdsNanos.p90)} · p99 {formatMicros(mdsNanos.p99)}
              </p>
            )}
            {maxMds !== undefined && <p>The ≤ {formatMicros(maxMds)} timing-spread cut-off is our own choice, not a Blitzortung threshold.</p>}
          </div>
        </>
      )}
    </section>
  );
}
