import { useState } from 'react';

import type { MapFocus } from '@shared/types/map-focus';
import { LiveMap } from '@features/live-map/containers/live-map';
import { StatsPanel } from '@features/stats/containers/stats-panel';
import { AppFooter } from '@app/layout/app-footer';
import { AppHeader } from '@app/layout/app-header';

/** Page layout: features are composed here and never import each other. */
export function App() {
  const [focus, setFocus] = useState<MapFocus | null>(null);

  const showOnMap = (h: { lon: number; lat: number }) => {
    setFocus((prev) => ({ lon: h.lon, lat: h.lat, zoom: 6, requestId: (prev?.requestId ?? 0) + 1 }));
    // stacked layout: the map sits above the panel, bring it back into view
    window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  };

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground lg:h-dvh">
      <AppHeader />
      <main className="grid flex-1 content-start gap-4 p-4 lg:min-h-0 lg:grid-cols-[1fr_22rem] lg:content-stretch">
        <LiveMap focus={focus} />
        <StatsPanel onHotspotSelect={showOnMap} />
      </main>
      <AppFooter />
    </div>
  );
}
