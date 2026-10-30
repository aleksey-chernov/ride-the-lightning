import type { Detection } from '@api/models/detection';
import { formatSeconds } from '@shared/utils/units.utils';

import { StatTile } from './stat-tile';

type Props = { detection: Detection };

/** How long strikes took to reach the feed in the last 10 minutes: publication delay percentiles. */
export function DetectionSection({ detection: { delaySeconds } }: Props) {
  return (
    <section className="space-y-3" aria-label="Publication delay, last 10 minutes">
      <h3 className="text-xs font-medium text-muted-foreground">Publication delay, last 10 min</h3>
      {delaySeconds ? (
        <div className="grid grid-cols-3 gap-2">
          <StatTile label="Median" value={formatSeconds(delaySeconds.p50)} />
          <StatTile label="p90" value={formatSeconds(delaySeconds.p90)} />
          <StatTile label="p99" value={formatSeconds(delaySeconds.p99)} />
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">No strikes in the last 10 minutes</p>
      )}
    </section>
  );
}
