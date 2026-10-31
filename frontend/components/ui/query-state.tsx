'use client'

// Composant repris d'EcoleManagement (design system commun).

import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'
import { useT } from '@/providers/locale-provider'
import { AlertTriangle } from 'lucide-react'

export function QueryErrorNotice({
  error, errorLabel, retryLabel, onRetry, className = '',
}: {
  error: unknown
  errorLabel?: string
  retryLabel?: string
  onRetry?: () => void
  className?: string
}) {
  const t = useT()
  return (
    <div
      role="alert"
      className={`flex flex-col items-start gap-2 rounded-lg bg-destructive/5 p-3 ring-1 ring-destructive/20 ${className}`}
    >
      <p className="flex items-center gap-2 text-sm text-foreground">
        <AlertTriangle className="h-4 w-4 shrink-0 text-destructive" aria-hidden />
        {(error as Error)?.message || errorLabel || t('qs.loadError')}
      </p>
      {onRetry && (
        <Button size="sm" variant="secondary" onClick={onRetry}>{retryLabel || t('qs.retry')}</Button>
      )}
    </div>
  )
}

export function QueryState({
  loading, error, empty, emptyLabel, emptyAction, errorLabel, retryLabel, rows = 3, children, onRetry, hasData,
}: {
  loading?: boolean
  /** Erreur de la requête ; passer `isError ? error : null` (falsy = pas d'erreur). */
  error?: unknown
  /** Vrai quand la requête a abouti mais ne renvoie rien. */
  empty?: boolean
  /**
   * Vrai quand l'écran tient déjà une donnée servie précédemment (`rows.length > 0`,
   * `data != null`…). En cas d'erreur, elle est conservée et l'alerte se pose au-dessus.
   */
  hasData?: boolean
  emptyLabel?: string
  /** Action proposée dans l'état vide (ex. « Créer votre première faculté »). */
  emptyAction?: React.ReactNode
  /** Libellé d'erreur si l'exception n'en porte pas. */
  errorLabel?: string
  retryLabel?: string
  rows?: number
  children?: React.ReactNode
  onRetry?: () => void
}) {
  const t = useT()

  if (loading) {
    return (
      <div className="flex flex-col gap-2" aria-busy="true">
        {Array.from({ length: rows }, (_, i) => (
          <Skeleton key={i} className="h-8" style={{ width: `${92 - (i % 3) * 14}%` }} />
        ))}
      </div>
    )
  }

  // Rien à conserver : l'erreur PREND la place du contenu.
  if (error && !hasData) {
    return (
      <QueryErrorNotice error={error} errorLabel={errorLabel} retryLabel={retryLabel} onRetry={onRetry} />
    )
  }

  if (empty && !error) {
    return (
      <div className="flex flex-col items-start gap-2 py-1">
        <p className="text-sm text-muted-foreground">{emptyLabel ?? t('qs.empty')}</p>
        {emptyAction}
      </div>
    )
  }

  // Donnée périmée conservée + alerte au-dessus (échec d'un rafraîchissement).
  return (
    <>
      {!!error && (
        <QueryErrorNotice
          error={error} errorLabel={errorLabel} retryLabel={retryLabel} onRetry={onRetry}
          className="mb-3"
        />
      )}
      {children}
    </>
  )
}
