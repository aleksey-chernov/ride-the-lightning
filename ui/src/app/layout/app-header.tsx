import { Zap } from 'lucide-react';

import { useGetVersion } from '@api/endpoints/admin/admin';
import { ConnectionBadge } from '@shared/components/connection-badge';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

/** App header; the single connection indicator is driven by the live strike feed. */
export function AppHeader() {
  const status = useLiveFeedStore((s) => s.status);
  const { data: version } = useGetVersion({ query: { staleTime: Infinity } });

  return (
    <header className="flex h-14 items-center justify-between gap-3 border-b px-4">
      <div className="flex min-w-0 items-center gap-2">
        <Zap className="size-5 shrink-0 fill-yellow-400 text-yellow-400" />
        <h1 className="truncate font-heading text-lg font-semibold tracking-tight whitespace-nowrap">Ride the Lightning</h1>
      </div>
      <ConnectionBadge status={status} version={version?.version} />
    </header>
  );
}
