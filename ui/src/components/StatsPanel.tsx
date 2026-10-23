import { PanelRightClose, PanelRightOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

type Props = { open: boolean; onOpenChange: (open: boolean) => void }

export function StatsPanel({ open, onOpenChange }: Props) {
  if (!open) {
    return (
      <Card className="items-center p-2">
        <Button variant="ghost" size="icon" aria-label="Show stats" aria-expanded={false} onClick={() => onOpenChange(true)}>
          <PanelRightOpen />
        </Button>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Stats</CardTitle>
        <CardDescription>Arrive in Phase 7</CardDescription>
        <CardAction>
          <Button variant="ghost" size="icon" aria-label="Hide stats" aria-expanded onClick={() => onOpenChange(false)}>
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
  )
}
