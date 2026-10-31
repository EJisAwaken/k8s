import type { StatusStyleMap } from '@/components/ui/status-chip'
import type { StatutInventaire } from '@/lib/api/types'

export const STATUT_INVENTAIRE: StatusStyleMap<StatutInventaire> = {
  EN_COURS: { variant: 'blue', labelKey: 'En cours de comptage' },
  A_VALIDER: { variant: 'yellow', labelKey: 'À valider' },
  VALIDE: { variant: 'green', labelKey: 'Validé' },
  ANNULE: { variant: 'gray', labelKey: 'Annulé' },
}
