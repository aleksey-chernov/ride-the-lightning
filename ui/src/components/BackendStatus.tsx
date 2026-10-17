import { useHealth, useVersion } from '@/api/queries'
import { Badge } from '@/components/ui/badge'

export function BackendStatus() {
  const health = useHealth()
  const version = useVersion()

  if (health.isPending) return <Badge variant="outline">connecting…</Badge>
  if (health.isError) return <Badge variant="destructive">backend offline</Badge>

  return (
    <Badge variant="outline" className="gap-1.5">
      <span className="size-2 rounded-full bg-emerald-500" />
      backend {version.data?.version ?? ''}
    </Badge>
  )
}
