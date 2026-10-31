'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Moon, PackageX, TriangleAlert } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { QueryState } from '@/components/ui/query-state'
import { api } from '@/lib/api/client'
import type { Alertes, StockLigne } from '@/lib/api/types'
import { useDepots } from '@/lib/api/hooks'
import { jour, qte } from '@/lib/format'

export default function AlertesStock() {
  const depots = useDepots()
  const [depotId, setDepotId] = useState('')
  const alertes = useQuery({
    queryKey: ['alertes', depotId],
    queryFn: () => api.get<Alertes>('/stock/alertes', { depotId }),
  })

  return (
    <Page>
      <PageHeader
        title="Alertes de stock"
        subtitle="Ruptures, articles sous leur seuil minimum, et stock dormant."
        actions={
          <Select value={depotId} onChange={(e) => setDepotId(e.target.value)} aria-label="Dépôt">
            <option value="">Tous les dépôts</option>
            {depots.data?.map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
        }
      />
      <QueryState loading={alertes.isLoading} error={alertes.error} onRetry={() => void alertes.refetch()}>
        {alertes.data && (
          <div className="grid gap-4 lg:grid-cols-3">
            <Section titre="Ruptures" icone={<PackageX size={16} />} ton="text-destructive" lignes={alertes.data.ruptures} vide="Aucune rupture." />
            <Section titre="Sous le seuil" icone={<TriangleAlert size={16} />} ton="text-warning" lignes={alertes.data.sousSeuil} vide="Aucun article sous son seuil." seuil />
            <Section
              titre={`Dormants (> ${alertes.data.joursDormance} j sans sortie)`}
              icone={<Moon size={16} />}
              ton="text-muted-foreground"
              lignes={alertes.data.dormants}
              vide="Aucun stock dormant."
              dormant
            />
          </div>
        )}
      </QueryState>
    </Page>
  )
}

function Section({ titre, icone, ton, lignes, vide, seuil, dormant }: {
  titre: string; icone: React.ReactNode; ton: string; lignes: StockLigne[]; vide: string; seuil?: boolean; dormant?: boolean
}) {
  return (
    <Card padding="none">
      <h2 className={`flex items-center gap-2 border-b border-border px-5 py-4 font-semibold ${ton}`}>
        {icone} {titre} <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">{lignes.length}</span>
      </h2>
      {lignes.length === 0 ? (
        <p className="px-5 py-6 text-sm text-muted-foreground">{vide}</p>
      ) : (
        <ul className="max-h-[60vh] divide-y divide-border overflow-y-auto">
          {lignes.map((l) => (
            <li key={`${l.varianteId}-${l.depotId}`} className="flex items-center justify-between gap-3 px-5 py-3 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{l.libelle}</p>
                <p className="text-2xs text-muted-foreground">{l.sku} · {l.depotNom}</p>
              </div>
              <div className="shrink-0 text-right">
                <p className="font-semibold tabular-nums">{qte(dormant ? l.qtePhysique : l.qteDisponible)}</p>
                <p className="text-2xs text-muted-foreground">
                  {seuil && `seuil ${qte(l.seuilMini)}`}
                  {dormant && `dernière sortie ${l.derniereSortieAt ? jour(l.derniereSortieAt) : 'jamais'}`}
                  {!seuil && !dormant && 'disponible'}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
