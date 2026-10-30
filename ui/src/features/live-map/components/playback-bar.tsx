import { History, Radio, RotateCw } from 'lucide-react';
import type { ReactNode } from 'react';

import { Button } from '@shared/components/ui/button';
import { ToggleGroup, ToggleGroupItem } from '@shared/components/ui/toggle-group';
import type { MapMode } from '@features/live-map/stores/playback.store';

type Props = {
  mode: MapMode;
  /** false while nothing is stored yet */
  canPlayback: boolean;
  onModeChange: (mode: MapMode) => void;
  /** loading, empty, error or thinning note; empty for none */
  status: string;
  onRetry?: () => void;
  /** playback controls, shown between the mode switch and the status */
  children?: ReactNode;
};

/** The bar below the map; the mode switch is always its first child, so keyboard focus survives a mode change. */
export function PlaybackBar({ mode, canPlayback, onModeChange, status, onRetry, children }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2 border-t p-2">
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        spacing={0}
        aria-label="Map mode"
        value={mode}
        onValueChange={(v) => v && onModeChange(v as MapMode)}
      >
        <ToggleGroupItem value="live">
          <Radio data-icon="inline-start" />
          Live
        </ToggleGroupItem>
        <ToggleGroupItem value="playback" disabled={!canPlayback} title={canPlayback ? undefined : 'No stored strikes yet'}>
          <History data-icon="inline-start" />
          Playback
        </ToggleGroupItem>
      </ToggleGroup>
      {children}
      {/* sr-only, not hidden, when empty: a live region must stay in the accessibility tree to announce */}
      <p role="status" className="text-xs text-muted-foreground empty:sr-only">
        {status}
      </p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCw data-icon="inline-start" />
          Retry
        </Button>
      )}
    </div>
  );
}
