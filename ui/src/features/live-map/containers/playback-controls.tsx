import { useShallow } from 'zustand/react/shallow';

import { PlaybackBar } from '@features/live-map/components/playback-bar';
import { PlaybackOptions } from '@features/live-map/components/playback-options';
import { PlaybackTransport } from '@features/live-map/components/playback-transport';
import type { PresetId } from '@features/live-map/constants/playback';
import { useHistoryStrikes } from '@features/live-map/hooks/use-history-strikes';
import { usePlaybackTicker } from '@features/live-map/hooks/use-playback-ticker';
import { selectMapMode, usePlaybackStore, type MapMode } from '@features/live-map/stores/playback.store';
import { formatPlayhead, isPresetAvailable, presetOf, startPreset } from '@features/live-map/utils/playback.utils';

type Props = {
  /** oldest stored strike, epoch ms; absent while nothing is stored */
  oldestMs?: number;
};

/** The one line for screen readers and eyes: loading, failure, nothing stored, or thinning. */
function strikesStatus(strikes: ReturnType<typeof useHistoryStrikes>): string {
  if (strikes.isError) return strikes.error instanceof Error ? strikes.error.message : 'Could not load stored strikes';
  if (!strikes.data) return 'Loading strikes…';
  if (strikes.data.strikes.length === 0) return 'No strikes stored for this range';
  return strikes.data.step > 1 ? `Showing 1 in ${strikes.data.step} strikes` : '';
}

/** Playback bar: owns the playhead subscription (re-renders on every tick) so the map and LiveMap don't. */
export function PlaybackControls({ oldestMs }: Props) {
  const mode = usePlaybackStore(selectMapMode);
  const { range, preset, speed, positionMs, playing } = usePlaybackStore(
    useShallow((s) => ({ range: s.range, preset: s.preset, speed: s.speed, positionMs: s.positionMs, playing: s.playing })),
  );
  const { start, stop, play, pause, seek, setSpeed } = usePlaybackStore(
    useShallow((s) => ({ start: s.start, stop: s.stop, play: s.play, pause: s.pause, seek: s.seek, setSpeed: s.setSpeed })),
  );
  const strikes = useHistoryStrikes();
  usePlaybackTicker(strikes.isSuccess);

  // ranges end at the time they start, so what they can reach is end − oldest, not newest − oldest
  const spanTo = (endMs: number) => (oldestMs !== undefined ? endMs - oldestMs : 0);
  const startAt = (p: PresetId) => start(p, Date.now(), oldestMs);
  const onModeChange = (m: MapMode) => (m === 'playback' ? startAt(startPreset(spanTo(Date.now()))) : stop());

  const status = mode === 'live' ? (oldestMs === undefined ? 'No stored strikes yet' : '') : strikesStatus(strikes);

  return (
    <PlaybackBar
      mode={mode}
      canPlayback={oldestMs !== undefined}
      onModeChange={onModeChange}
      status={status}
      onRetry={mode === 'playback' && strikes.isError ? () => void strikes.refetch() : undefined}
    >
      {range && (
        <>
          <PlaybackTransport
            loading={!strikes.isSuccess}
            playing={playing}
            onPlayingChange={(p) => (p ? play() : pause())}
            fromMs={range.fromMs}
            toMs={range.toMs}
            positionMs={positionMs}
            stepMs={presetOf(preset).sliderStepMs}
            label={formatPlayhead(positionMs, range.toMs - range.fromMs)}
            onSeek={seek}
          />
          <PlaybackOptions
            preset={preset}
            isPresetAvailable={(p) => isPresetAvailable(p, spanTo(range.toMs))}
            onPresetChange={startAt}
            speed={speed}
            onSpeedChange={setSpeed}
          />
        </>
      )}
    </PlaybackBar>
  );
}
