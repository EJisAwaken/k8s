// Composant repris d'EcoleManagement (design system commun).
import { cn } from '@/lib/utils'
import { Card } from '@/components/ui/card'

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('skeleton-sweep animate-pulse rounded-md bg-muted', className)} {...props} />
}

/** Squelette d'une carte (titre + quelques lignes) — pour les pages en cartes/formulaires. */
export function CardSkeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <Card className={cn('space-y-3', className)}>
      <Skeleton className="h-4 w-1/3" />
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className="h-3" style={{ width: `${88 - i * 14}%` }} />
      ))}
    </Card>
  )
}

export function TableSkeleton({ rows = 6, cols = 4, avatar = true }: {
  rows?: number; cols?: number; avatar?: boolean
}) {
  return (
    <div className="overflow-hidden rounded-xl bg-card shadow-sm ring-1 ring-border">
      <div className="flex gap-4 bg-muted px-4 py-3">
        {Array.from({ length: cols }).map((_, c) => (
          <Skeleton key={c} className="h-3" style={{ width: c === 0 ? '32%' : `${14 + (c * 7) % 10}%` }} />
        ))}
      </div>
      <div className="divide-y divide-border">
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="flex items-center gap-4 px-4 py-3.5">
            {Array.from({ length: cols }).map((_, c) => (
              c === 0 && avatar ? (
                <div key={c} className="flex items-center gap-2.5" style={{ width: '32%' }}>
                  <Skeleton className="h-6 w-6 shrink-0 rounded-full" />
                  <Skeleton className="h-4 flex-1" />
                </div>
              ) : (
                <Skeleton key={c} className="h-4"
                  style={{ width: `${12 + ((c * 13 + r * 7) % 22)}%` }} />
              )
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}
