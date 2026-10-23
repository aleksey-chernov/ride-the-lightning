import { StatsPanelCard } from '@features/stats/components/stats-panel-card';
import { StatsPanelRail } from '@features/stats/components/stats-panel-rail';
import { useStatsPanelStore } from '@features/stats/stores/stats-panel.store';

/** Stats feature: switches between the folded rail and the full card. */
export function StatsPanel() {
  const open = useStatsPanelStore((s) => s.open);
  const setOpen = useStatsPanelStore((s) => s.setOpen);

  return open ? <StatsPanelCard onCollapse={() => setOpen(false)} /> : <StatsPanelRail onExpand={() => setOpen(true)} />;
}
