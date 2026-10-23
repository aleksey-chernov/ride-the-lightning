import { cn } from 'cn';

import { LiveMap } from '@features/live-map/containers/live-map';
import { StatsPanel } from '@features/stats/containers/stats-panel';
import { useStatsPanelStore } from '@features/stats/stores/stats-panel.store';
import { AppFooter } from '@app/layout/app-footer';
import { AppHeader } from '@app/layout/app-header';

/** Page layout: features are composed here and never import each other. */
export function App() {
  const statsOpen = useStatsPanelStore((s) => s.open);

  return (
    <div className="flex h-dvh flex-col bg-background text-foreground">
      <AppHeader />
      <main
        className={cn(
          'grid min-h-0 flex-1 gap-4 p-4 transition-[grid-template-columns] duration-300 ease-out',
          statsOpen ? 'lg:grid-cols-[1fr_22rem]' : 'lg:grid-cols-[1fr_3.5rem]',
        )}
      >
        <LiveMap />
        <StatsPanel />
      </main>
      <AppFooter />
    </div>
  );
}
