import { formatCount, formatDuplicateRate } from '@features/stats/utils/stats-format.utils';

import { StatTile } from './stat-tile';

type Props = { lastMinute: number; last10Minutes: number; lastHour: number; received: number; duplicatesDropped: number };

/** Strike counts for the last minute, 10 minutes and hour, plus ingest totals. */
export function StatsHeadline({ lastMinute, last10Minutes, lastHour, received, duplicatesDropped }: Props) {
  return (
    <section className="space-y-2" aria-label="Strike counts">
      <div className="grid grid-cols-3 gap-2">
        <StatTile label="Last minute" value={formatCount(lastMinute)} />
        <StatTile label="Last 10 min" value={formatCount(last10Minutes)} />
        <StatTile label="Last hour" value={formatCount(lastHour)} />
      </div>
      <p className="text-xs text-muted-foreground tabular-nums">
        {formatCount(received)} received · {formatCount(duplicatesDropped)} duplicates dropped ({formatDuplicateRate(received, duplicatesDropped)}%)
      </p>
    </section>
  );
}
