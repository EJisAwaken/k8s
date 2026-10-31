import { clsx } from 'clsx'
import type { LucideIcon } from 'lucide-react'
import Link from 'next/link'

/** Tuile d'indicateur du tableau de bord (forme du StatTile d'EcoleManagement, sans sparkline). */
export function StatTile({
  label,
  value,
  hint,
  icon: Icon,
  tone = 'default',
  href,
}: {
  label: string
  value: React.ReactNode
  hint?: React.ReactNode
  icon?: LucideIcon
  tone?: 'default' | 'warning' | 'danger' | 'success'
  href?: string
}) {
  const contenu = (
    <div className={clsx(
      'h-full rounded-xl border border-border bg-card p-5 shadow-sm transition-colors',
      href && 'hover:border-primary/40 hover:bg-muted/40',
    )}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{label}</p>
        {Icon && (
          <span className={clsx('rounded-lg p-2', {
            'bg-primary/5 text-primary': tone === 'default',
            'bg-warning/5 text-warning': tone === 'warning',
            'bg-destructive/5 text-destructive': tone === 'danger',
            'bg-success/5 text-success': tone === 'success',
          })}>
            <Icon size={16} />
          </span>
        )}
      </div>
      <p className="mt-2 text-2xl font-bold tabular-nums tracking-tight">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
  return href ? <Link href={href} className="block h-full">{contenu}</Link> : contenu
}
