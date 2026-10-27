import { keepPreviousData } from '@tanstack/react-query';

import { useGetStats } from '@api/endpoints/stats/stats';
import { selectMaxMds, useStrikeFilterStore } from '@shared/stores/strike-filter.store';
import { StatsPanelCard } from '@features/stats/components/stats-panel-card';

type Props = { onHotspotSelect: (hotspot: { lon: number; lat: number }) => void };

/** Stats feature: polls stats every 5 s with the shared strike filter and renders the card. */
export function StatsPanel({ onHotspotSelect }: Props) {
  const maxMds = useStrikeFilterStore(selectMaxMds);
  // the filter is part of the query key, so toggling it refetches; the old numbers stay up (dimmed) meanwhile
  const stats = useGetStats(maxMds === null ? undefined : { maxMds }, { query: { refetchInterval: 5000, placeholderData: keepPreviousData } });
  // without data, react-query resets to pending on every refetch; errorUpdateCount keeps the failure visible meanwhile
  const isError = stats.isError || (stats.data === undefined && stats.errorUpdateCount > 0);

  return <StatsPanelCard stats={stats.data} isError={isError} isPlaceholder={stats.isPlaceholderData} onHotspotSelect={onHotspotSelect} />;
}
