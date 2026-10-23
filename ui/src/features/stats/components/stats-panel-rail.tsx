import { PanelRightOpen } from 'lucide-react';

import { Button } from '@shared/components/ui/button';
import { Card } from '@shared/components/ui/card';

type Props = { onExpand: () => void };

/** The folded panel: a slim rail with an expand button. */
export function StatsPanelRail({ onExpand }: Props) {
  return (
    <Card className="items-center p-2">
      <Button variant="ghost" size="icon" aria-label="Show stats" aria-expanded={false} onClick={onExpand}>
        <PanelRightOpen />
      </Button>
    </Card>
  );
}
