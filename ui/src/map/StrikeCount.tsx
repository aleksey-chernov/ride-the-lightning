import { Badge } from '@/components/ui/badge'

export function StrikeCount({ count, minutes }: { count: number; minutes: number }) {
  return (
    <Badge variant="outline" className="backdrop-blur">
      {count.toLocaleString()} strikes · {minutes} min
    </Badge>
  )
}
