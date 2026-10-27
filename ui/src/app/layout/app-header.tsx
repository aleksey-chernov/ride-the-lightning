import { Zap } from 'lucide-react';

import { useGetVersion } from '@api/endpoints/admin/admin';
import { ConnectionBadge } from '@shared/components/connection-badge';
import { useStrikeFilterStore } from '@shared/stores/strike-filter.store';
import { useLiveFeedStore } from '@features/live-map/stores/live-feed.store';

import { StrikeFilterToggle } from './strike-filter-toggle';

/** App header; the single connection indicator is driven by the live strike feed. */
export function AppHeader() {
  const status = useLiveFeedStore((s) => s.status);
  const tightOnly = useStrikeFilterStore((s) => s.tightOnly);
  const setTightOnly = useStrikeFilterStore((s) => s.setTightOnly);
  const { data: version } = useGetVersion({ query: { staleTime: Infinity } });

  return (
    <header className="flex h-14 items-center justify-between gap-3 border-b px-4">
      <div className="flex min-w-0 items-center gap-2">
        <Zap className="size-5 shrink-0 fill-yellow-400 text-yellow-400" />
        <h1 className="truncate font-heading text-base font-semibold tracking-tight whitespace-nowrap sm:text-lg">Ride the Lightning</h1>
      </div>
      <div className="flex shrink-0 items-center gap-2 sm:gap-4">
        <StrikeFilterToggle checked={tightOnly} onCheckedChange={setTightOnly} />
        <ConnectionBadge status={status} version={version?.version} />
      </div>
    </header>
  );
}
