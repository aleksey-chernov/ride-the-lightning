type Props = { label: string; value: string };

/** A small labelled figure; the caller formats the value (counts, units). */
export function StatTile({ label, value }: Props) {
  return (
    <div className="rounded-lg bg-muted/50 px-3 py-2">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="text-lg font-medium tabular-nums">{value}</div>
    </div>
  );
}
