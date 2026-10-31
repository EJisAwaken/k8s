'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useState } from 'react'
import { clsx } from 'clsx'
import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react'

export type SortDir = 'asc' | 'desc'
export interface SortState { field: string; dir: SortDir }

/**
 * État de tri d'un tableau paginé côté serveur.
 * `param` est prêt à être passé à Spring Data (`?sort=champ,asc`).
 */
export function useTableSort(initial?: SortState) {
  const [sort, setSort] = useState<SortState | null>(initial ?? null)
  const toggle = (field: string) =>
    setSort(prev =>
      prev?.field === field
        ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { field, dir: 'asc' },
    )
  const param = sort ? `${sort.field},${sort.dir}` : undefined
  return { sort, toggle, param, setSort }
}

interface SortHeaderProps {
  label: string
  field: string
  sort: SortState | null
  onSort: (field: string) => void
  className?: string
  /** Aligne l'indicateur à droite (colonnes numériques). */
  align?: 'left' | 'right'
}

/** En-tête de colonne cliquable avec indicateur de sens de tri. */
export function SortHeader({ label, field, sort, onSort, className, align = 'left' }: SortHeaderProps) {
  const active = sort?.field === field
  return (
    <th className={clsx('px-4 py-3 pointer-coarse:py-0', className)}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className={clsx(
          'inline-flex items-center gap-1 font-semibold uppercase tracking-wide transition-colors hover:text-foreground',
          // Plancher tactile : l'en-tête de tri ne mesurait que la hauteur de son
          // texte (~16 px). PIXELS et non `rem` — cf. `button.tsx`.
          'pointer-coarse:min-h-[44px]',
          align === 'right' && 'flex-row-reverse',
          active ? 'text-foreground' : 'text-muted-foreground',
        )}
      >
        {label}
        {active
          ? (sort!.dir === 'asc' ? <ChevronUp size={13} /> : <ChevronDown size={13} />)
          : <ChevronsUpDown size={13} className="text-muted-foreground" />}
      </button>
    </th>
  )
}
