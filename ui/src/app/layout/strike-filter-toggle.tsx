import { useId } from 'react';

import { Switch } from '@shared/components/ui/switch';
import { TIGHT_MDS_NS } from '@shared/stores/strike-filter.store';
import { formatMicros } from '@shared/utils/units.utils';

type Props = { checked: boolean; onCheckedChange: (checked: boolean) => void };

/** Header switch for the timing-spread filter; the visible label appears from `sm`, the switch keeps its full name. */
export function StrikeFilterToggle({ checked, onCheckedChange }: Props) {
  const id = useId();
  const limit = formatMicros(TIGHT_MDS_NS);

  return (
    <div className="flex items-center gap-2">
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} aria-label={`Only strikes with timing spread ≤ ${limit}`} />
      <label htmlFor={id} className="hidden text-sm whitespace-nowrap text-muted-foreground sm:inline">
        Timing spread ≤ {limit}
      </label>
    </div>
  );
}
