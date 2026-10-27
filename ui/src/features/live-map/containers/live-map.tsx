import { Card } from '@shared/components/ui/card';
import { selectMaxMds, useStrikeFilterStore } from '@shared/stores/strike-filter.store';
import type { MapFocus } from '@shared/types/map-focus';
import { StrikeMap } from '@features/live-map/components/strike-map';
import { useLiveStrikes } from '@features/live-map/hooks/use-live-strikes';

const WINDOW_MINUTES = 15;

type Props = { focus: MapFocus | null };

/** Live map feature: connects to the strike feed and renders the map, filtered by the shared strike filter. */
export function LiveMap({ focus }: Props) {
  const buffer = useLiveStrikes(WINDOW_MINUTES);
  const maxMds = useStrikeFilterStore(selectMaxMds);

  return (
    <Card className="relative h-[60dvh] min-h-80 overflow-hidden p-0 has-[canvas:focus-visible]:ring-3 has-[canvas:focus-visible]:ring-ring/50 lg:h-auto lg:min-h-96 [&_canvas]:outline-none">
      <StrikeMap buffer={buffer} focus={focus} maxMds={maxMds} />
    </Card>
  );
}
