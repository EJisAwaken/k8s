'use client'

// Composant repris d'EcoleManagement (design system commun).

import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './button'
import { useT } from '@/providers/locale-provider'
import { resolveWith } from '@/lib/i18n'

interface PaginationProps {
  /** Index de page courant (0-indexé, comme Spring Data). */
  page: number
  /** Nombre total de pages. */
  totalPages: number
  /** Nombre total d'éléments (tous filtres appliqués). */
  totalElements: number
  /** Taille d'une page. */
  size: number
  onPageChange: (page: number) => void
  label?: string
  labelPlural?: string
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  label,
  labelPlural,
}: PaginationProps) {
  const t = useT()
  // Repli sur le français littéral si la clé manque : une clé brute
  // (« pg.prev ») affichée à l'écran est pire qu'un mot dans la mauvaise langue.
  const tx = (key: string, fallbackFr: string, vars?: Record<string, string | number>) =>
    resolveWith(t, key, fallbackFr, vars)

  if (totalElements === 0) return null

  const from = page * size + 1
  const to = Math.min(page * size + size, totalElements)
  const canPrev = page > 0
  const canNext = page < totalPages - 1

  // Le nom n'apparaît que si les DEUX formes sont connues (cf. en-tête).
  const noun = label && labelPlural ? (totalElements > 1 ? labelPlural : label) : null

  return (
    <div className="mt-3 flex items-center justify-between">
      <p className="text-xs tabular-nums text-muted-foreground">
        {noun === null
          ? tx('pg.range', '{from}–{to} sur {total}', { from, to, total: totalElements })
          : tx('pg.rangeOf', '{from}–{to} sur {total} {label}',
              { from, to, total: totalElements, label: noun })}
      </p>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            disabled={!canPrev}
            onClick={() => canPrev && onPageChange(page - 1)}
          >
            <ChevronLeft size={14} /> {tx('pg.prev', 'Précédent')}
          </Button>
          <span className="text-xs tabular-nums text-muted-foreground">
            {tx('pg.page', 'Page {page} / {total}', { page: page + 1, total: totalPages })}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={!canNext}
            onClick={() => canNext && onPageChange(page + 1)}
          >
            {tx('pg.next', 'Suivant')} <ChevronRight size={14} />
          </Button>
        </div>
      )}
    </div>
  )
}
