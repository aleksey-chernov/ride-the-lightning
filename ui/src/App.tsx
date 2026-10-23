import { AppHeader } from '@/components/AppHeader'
import { Attribution } from '@/components/Attribution'
import { StatsPanel } from '@/components/StatsPanel'
import { Card } from '@/components/ui/card'
import { usePersistedFlag } from '@/lib/usePersistedFlag'
import { cn } from '@/lib/utils'
import { StrikeCount } from '@/map/StrikeCount'
import { StrikeMap } from '@/map/StrikeMap'
import { useLiveStrikes } from '@/map/useLiveStrikes'

export default function App() {
  const { buffer, status, count, windowMinutes } = useLiveStrikes()
  const [statsOpen, setStatsOpen] = usePersistedFlag('rtl.statsOpen', true)

  return (
    <div className="flex h-dvh flex-col bg-background text-foreground">
      <AppHeader status={status} />
      <main
        className={cn(
          'grid min-h-0 flex-1 gap-4 p-4 transition-[grid-template-columns] duration-300 ease-out',
          statsOpen ? 'lg:grid-cols-[1fr_22rem]' : 'lg:grid-cols-[1fr_3.5rem]',
        )}
      >
        <Card className="relative min-h-96 overflow-hidden p-0">
          <StrikeMap buffer={buffer} />
          <div className="absolute top-3 left-3">
            <StrikeCount count={count} minutes={windowMinutes} />
          </div>
        </Card>
        <StatsPanel open={statsOpen} onOpenChange={setStatsOpen} />
      </main>
      <Attribution />
    </div>
  )
}
