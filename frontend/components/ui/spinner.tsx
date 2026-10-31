// Composant repris d'EcoleManagement (design system commun).
import { Loader2 } from 'lucide-react'
import { clsx } from 'clsx'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={clsx('animate-spin text-primary', className)} />
}

export function PageSpinner() {
  return (
    <div className="flex h-64 items-center justify-center">
      <Spinner className="h-8 w-8" />
    </div>
  )
}
