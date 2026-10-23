import { Badge } from '@shared/components/ui/badge';

type Props = { count: number; minutes: number };

export function StrikeCount({ count, minutes }: Props) {
  return (
    <Badge variant="outline" className="backdrop-blur">
      {count.toLocaleString()} strikes · {minutes} min
    </Badge>
  );
}
