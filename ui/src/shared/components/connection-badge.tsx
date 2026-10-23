import { Badge } from '@shared/components/ui/badge';
import type { ConnectionStatus } from '@shared/types/connection-status';

type Props = { status: ConnectionStatus; version?: string };

/** Connection indicator: presentational, the caller decides what "status" means. */
export function ConnectionBadge({ status, version }: Props) {
  if (status === 'connecting') return <Badge variant="outline">connecting…</Badge>;
  if (status === 'reconnecting') return <Badge variant="destructive">backend offline · reconnecting…</Badge>;

  return (
    <Badge variant="outline" className="gap-1.5">
      <span className="size-2 animate-pulse rounded-full bg-yellow-400" />
      live{version && ` · backend ${version}`}
    </Badge>
  );
}
