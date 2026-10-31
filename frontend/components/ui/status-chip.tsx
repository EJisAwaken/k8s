'use client'

// Composant repris d'EcoleManagement (design system commun).

import type { ReactNode } from 'react'

import { Badge } from '@/components/ui/badge'
import { useT } from '@/providers/locale-provider'

/** Variantes de `Badge`, dérivées de ses props — jamais recopiées. */
export type StatusVariant = NonNullable<React.ComponentProps<typeof Badge>['variant']>

export interface StatusStyle {
  variant: StatusVariant
  labelKey: string
}

/** Table exhaustive `état → apparence`. Le compilateur exige un cas par valeur. */
export type StatusStyleMap<S extends string> = Record<S, StatusStyle>

export interface StatusChipProps<S extends string> {
  status: S | null | undefined
  styles: StatusStyleMap<S>
  /** Libellé imposé, qui l'emporte sur `labelKey` (état construit à l'exécution). */
  label?: ReactNode
  className?: string
}

export function StatusChip<S extends string>({ status, styles, label, className }: StatusChipProps<S>) {
  const t = useT()
  if (status == null || status === '') return null

  const style: StatusStyle | undefined = styles[status]
  const text = label ?? (style ? t(style.labelKey) : status)

  return <Badge variant={style?.variant ?? 'gray'} className={className}>{text}</Badge>
}
