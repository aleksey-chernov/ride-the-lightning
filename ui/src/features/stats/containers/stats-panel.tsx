import { useGetStats } from '@api/endpoints/stats/stats';
import { StatsPanelCard } from '@features/stats/components/stats-panel-card';

type Props = { onHotspotSelect: (hotspot: { lon: number; lat: number }) => void };

/** Stats feature: polls stats every 5 s and renders the card. */
export function StatsPanel({ onHotspotSelect }: Props) {
  const stats = useGetStats({ query: { refetchInterval: 5000 } });
  // without data, react-query resets to pending on every refetch; errorUpdateCount keeps the failure visible meanwhile
  const isError = stats.isError || (stats.data === undefined && stats.errorUpdateCount > 0);

  return <StatsPanelCard stats={stats.data} isError={isError} onHotspotSelect={onHotspotSelect} />;
}
