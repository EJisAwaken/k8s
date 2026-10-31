'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeftRight, Boxes, PackageX, TriangleAlert, Wallet, Warehouse } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { StatTile } from '@/components/ui/stat-tile'
import { Card } from '@/components/ui/card'
import { QueryState } from '@/components/ui/query-state'
import { api } from '@/lib/api/client'
import type { Alertes, Mouvement, PageDto, StockLigne } from '@/lib/api/types'
import { horodatage, mga, qte } from '@/lib/format'
import { useDepots } from '@/lib/api/hooks'
import { useSession } from '@/providers/session-provider'

export default function TableauDeBord() {
  const { user, voitLesCouts } = useSession()
  const depots = useDepots()
  const stock = useQuery({ queryKey: ['stock', 'tous'], queryFn: () => api.get<StockLigne[]>('/stock') })
  const alertes = useQuery({ queryKey: ['alertes', null], queryFn: () => api.get<Alertes>('/stock/alertes') })
  const derniers = useQuery({
    queryKey: ['mouvements', 'derniers'],
    queryFn: () => api.get<PageDto<Mouvement>>('/stock/mouvements', { size: 8 }),
  })

  const lignes = stock.data ?? []
  const valeur = lignes.reduce((s, l) => s + (l.valeur ?? 0), 0)
  const articlesEnStock = new Set(lignes.filter((l) => l.qtePhysique > 0).map((l) => l.varianteId)).size

  return (
    <Page>
      <PageHeader title={`Bonjour ${user.fullName.split(' ')[0]}`} subtitle="Vue d'ensemble du stock — lot 1 « Socle »." />

      <div className="stagger-in grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {voitLesCouts ? (
          <StatTile label="Valeur du stock" value={stock.isLoading ? '…' : mga(valeur)} hint="Au coût moyen pondéré (CUMP)" icon={Wallet} />
        ) : (
          <StatTile label="Dépôts actifs" value={depots.data?.filter((d) => d.actif).length ?? '…'} icon={Warehouse} />
        )}
        <StatTile label="Articles en stock" value={stock.isLoading ? '…' : qte(articlesEnStock)} hint="Variantes avec du physique" icon={Boxes} href="/stock" />
        <StatTile label="Ruptures" value={alertes.data?.ruptures.length ?? '…'} hint="Disponible à zéro" icon={PackageX} tone="danger" href="/stock/alertes" />
        <StatTile label="Sous le seuil" value={alertes.data?.sousSeuil.length ?? '…'} hint={`Dormants : ${alertes.data?.dormants.length ?? '…'}`} icon={TriangleAlert} tone="warning" href="/stock/alertes" />
      </div>

      <Card className="mt-6" padding="none">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h2 className="flex items-center gap-2 font-semibold"><ArrowLeftRight size={16} /> Derniers mouvements</h2>
          <Link href="/stock/mouvements" className="text-sm font-medium text-primary hover:underline">Tout voir</Link>
        </div>
        <div className="p-5">
        <QueryState loading={derniers.isLoading} error={derniers.error} empty={derniers.data?.content.length === 0} emptyLabel="Aucun mouvement pour l'instant." onRetry={() => void derniers.refetch()}>
          <ul className="-m-5 divide-y divide-border">
            {derniers.data?.content.map((m) => (
              <li key={m.id} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium">{m.libelle}</p>
                  <p className="text-xs text-muted-foreground">{m.typeLabel} · {m.depotNom} · {m.numero}</p>
                </div>
                <div className="shrink-0 text-right">
                  <p className={m.sens > 0 ? 'font-semibold text-success' : 'font-semibold text-destructive'}>
                    {m.sens > 0 ? '+' : '−'}{qte(m.quantite)}
                  </p>
                  <p className="text-xs text-muted-foreground">{horodatage(m.createdAt)}</p>
                </div>
              </li>
            ))}
          </ul>
        </QueryState>
        </div>
      </Card>
    </Page>
  )
}
