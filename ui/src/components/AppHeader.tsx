import { Zap } from 'lucide-react'
import type { StreamStatus } from '@/map/strikeStream'
import { BackendStatus } from './BackendStatus'

export function AppHeader({ status }: { status: StreamStatus }) {
  return (
    <header className="flex h-14 items-center justify-between border-b px-4">
      <div className="flex items-center gap-2">
        <Zap className="size-5 fill-yellow-400 text-yellow-400" />
        <h1 className="font-heading text-lg font-semibold tracking-tight">Ride the Lightning</h1>
      </div>
      <BackendStatus status={status} />
    </header>
  )
}
