import { useVersion } from '@/api/queries'
import { Badge } from '@/components/ui/badge'
import type { StreamStatus } from '@/map/strikeStream'

/** One connection indicator for the whole app, driven by the live strike stream. */
export function BackendStatus({ status }: { status: StreamStatus }) {
  const version = useVersion()

  if (status === 'connecting') return <Badge variant="outline">connecting…</Badge>
  if (status === 'reconnecting') return <Badge variant="destructive">backend offline · reconnecting…</Badge>

  return (
    <Badge variant="outline" className="gap-1.5">
      <span className="size-2 animate-pulse rounded-full bg-yellow-400" />
      live{version.data && ` · backend ${version.data.version}`}
    </Badge>
  )
}
