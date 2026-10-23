import { PanelRightClose } from 'lucide-react';

import { Button } from '@shared/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@shared/components/ui/card';
import { Skeleton } from '@shared/components/ui/skeleton';

type Props = { onCollapse: () => void };

/** The unfolded panel. Content arrives in Phase 7. */
export function StatsPanelCard({ onCollapse }: Props) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Stats</CardTitle>
        <CardDescription>Arrive in Phase 7</CardDescription>
        <CardAction>
          <Button variant="ghost" size="icon" aria-label="Hide stats" aria-expanded onClick={onCollapse}>
            <PanelRightClose />
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-40 w-full" />
      </CardContent>
    </Card>
  );
}
