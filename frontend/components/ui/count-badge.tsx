// Composant repris d'EcoleManagement (design system commun).

export function CountBadge({ count, className = '' }: { count: number; className?: string }) {
  if (count <= 0) return null
  return (
    <span
      className={`inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-3xs font-semibold leading-none text-primary-foreground ${className}`}
      aria-label={`${count} non lu${count > 1 ? 's' : ''}`}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}
