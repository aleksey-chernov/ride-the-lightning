import { AppHeader } from '@/components/AppHeader'
import { Attribution } from '@/components/Attribution'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export default function App() {
  return (
    <div className="flex h-dvh flex-col bg-background text-foreground">
      <AppHeader />
      <main className="grid min-h-0 flex-1 gap-4 p-4 lg:grid-cols-[1fr_22rem]">
        <Card className="min-h-80">
          <CardHeader>
            <CardTitle>Live map</CardTitle>
            <CardDescription>Arrives in Phase 6</CardDescription>
          </CardHeader>
          <CardContent className="flex-1">
            <Skeleton className="h-full min-h-60 w-full" />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Stats</CardTitle>
            <CardDescription>Arrive in Phase 7</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-40 w-full" />
          </CardContent>
        </Card>
      </main>
      <Attribution />
    </div>
  )
}
