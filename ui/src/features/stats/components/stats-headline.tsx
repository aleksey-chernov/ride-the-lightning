import { formatCount, formatDuplicateRate } from '@features/stats/utils/stats-format.utils';

type Props = { lastMinute: number; last10Minutes: number; lastHour: number; received: number; duplicatesDropped: number };

function Tile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-muted/50 px-3 py-2">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="text-lg font-medium tabular-nums">{formatCount(value)}</div>
    </div>
  );
}

/** Strike counts for the last minute, 10 minutes and hour, plus ingest totals. */
export function StatsHeadline({ lastMinute, last10Minutes, lastHour, received, duplicatesDropped }: Props) {
  return (
    <section className="space-y-2" aria-label="Strike counts">
      <div className="grid grid-cols-3 gap-2">
        <Tile label="Last minute" value={lastMinute} />
        <Tile label="Last 10 min" value={last10Minutes} />
        <Tile label="Last hour" value={lastHour} />
      </div>
      <p className="text-xs text-muted-foreground tabular-nums">
        {formatCount(received)} received · {formatCount(duplicatesDropped)} duplicates dropped ({formatDuplicateRate(received, duplicatesDropped)}%)
      </p>
    </section>
  );
}
