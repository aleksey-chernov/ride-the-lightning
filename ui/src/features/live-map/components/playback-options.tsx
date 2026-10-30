import { ToggleGroup, ToggleGroupItem } from '@shared/components/ui/toggle-group';
import { PRESETS, SPEEDS, type PresetId, type SpeedId } from '@features/live-map/constants/playback';

type Props = {
  preset: PresetId;
  isPresetAvailable: (preset: PresetId) => boolean;
  onPresetChange: (preset: PresetId) => void;
  speed: SpeedId;
  onSpeedChange: (speed: SpeedId) => void;
};

/** Range and speed pickers; Radix lets a single group be emptied by clicking the pressed item, so `""` is ignored. */
export function PlaybackOptions({ preset, isPresetAvailable, onPresetChange, speed, onSpeedChange }: Props) {
  return (
    <>
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        spacing={0}
        aria-label="Range"
        value={preset}
        onValueChange={(v) => v && onPresetChange(v as PresetId)}
      >
        {PRESETS.map((p) => (
          <ToggleGroupItem key={p.id} value={p.id} disabled={!isPresetAvailable(p.id)} aria-label={p.ariaLabel}>
            {p.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        spacing={0}
        aria-label="Speed"
        value={speed}
        onValueChange={(v) => v && onSpeedChange(v as SpeedId)}
      >
        {SPEEDS.map((s) => (
          <ToggleGroupItem key={s.id} value={s.id} aria-label={s.ariaLabel}>
            {s.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </>
  );
}
