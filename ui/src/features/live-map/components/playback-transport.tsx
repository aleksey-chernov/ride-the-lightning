import { Pause, Play } from 'lucide-react';

import { Button } from '@shared/components/ui/button';
import { Skeleton } from '@shared/components/ui/skeleton';
import { Slider } from '@shared/components/ui/slider';

type Props = {
  loading: boolean;
  playing: boolean;
  onPlayingChange: (playing: boolean) => void;
  fromMs: number;
  toMs: number;
  positionMs: number;
  stepMs: number;
  /** the playhead as text, e.g. `Tue 14:32` */
  label: string;
  onSeek: (ms: number) => void;
};

/** Play/pause, the time slider and the playhead time; a full row below `lg`. */
export function PlaybackTransport({ loading, playing, onPlayingChange, fromMs, toMs, positionMs, stepMs, label, onSeek }: Props) {
  return (
    <div className="flex w-full min-w-0 items-center gap-3 lg:w-auto lg:min-w-64 lg:flex-1">
      <Button variant="ghost" size="icon-sm" disabled={loading} aria-label={playing ? 'Pause' : 'Play'} onClick={() => onPlayingChange(!playing)}>
        {playing ? <Pause /> : <Play />}
      </Button>
      {loading ? (
        <Skeleton className="h-1 min-w-0 flex-1" />
      ) : (
        <Slider
          className="min-w-0 flex-1"
          min={fromMs}
          max={toMs}
          step={stepMs}
          value={[positionMs]}
          onValueChange={([ms]) => onSeek(ms)}
          thumbLabel="Playback time"
          thumbValueText={label}
        />
      )}
      {/* not a live region: it changes several times a second */}
      <span className="text-xs whitespace-nowrap text-muted-foreground tabular-nums">{loading ? '--:--' : label}</span>
    </div>
  );
}
