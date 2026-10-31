'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Download } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/input'
import { SearchField, useDebouncedValue } from '@/components/ui/search-field'
import { DataTable } from '@/components/ui/data-table'
import { api } from '@/lib/api/client'
import type { StockLigne } from '@/lib/api/types'
import { useDepots } from '@/lib/api/hooks'
import { mga, qte } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

export default function EtatStock() {
  const { voitLesCouts } = useSession()
  const depots = useDepots()
  const [depotId, setDepotId] = useState('')
  const [q, setQ] = useState('')
  const [avecStock, setAvecStock] = useState(true)
  const recherche = useDebouncedValue(q, 300)

  const stock = useQuery({
    queryKey: ['stock', depotId, recherche, avecStock],
    queryFn: () => api.get<StockLigne[]>('/stock', { depotId, q: recherche, avecStockSeulement: avecStock }),
  })

  const colonnes = useMemo<ColumnDef<StockLigne>[]>(() => {
    const base: ColumnDef<StockLigne>[] = [
      {
        id: 'article',
        header: 'Article',
        accessorFn: (l) => l.libelle,
        cell: ({ row }) => (
          <div className="min-w-56">
            <p className="font-medium">{row.original.libelle}</p>
            <p className="text-2xs text-muted-foreground">{row.original.sku}</p>
          </div>
        ),
      },
      { id: 'depot', header: 'Dépôt', accessorKey: 'depotNom' },
      { id: 'emplacement', header: 'Empl.', accessorFn: (l) => l.emplacementCode ?? '—' },
      { id: 'transit', header: 'En transit', accessorKey: 'qteTransit', cell: ({ getValue }) => <Num v={getValue<number>()} /> },
      { id: 'physique', header: 'Physique', accessorKey: 'qtePhysique', cell: ({ getValue }) => <Num v={getValue<number>()} gras /> },
      { id: 'reserve', header: 'Réservé', accessorKey: 'qteReservee', cell: ({ getValue }) => <Num v={getValue<number>()} /> },
      {
        id: 'disponible',
        header: 'Disponible',
        accessorKey: 'qteDisponible',
        cell: ({ row }) => {
          const l = row.original
          const ton = l.qteDisponible <= 0 ? 'text-destructive' : l.qteDisponible <= l.seuilMini ? 'text-warning' : ''
          return <span className={`block text-right font-semibold tabular-nums ${ton}`}>{qte(l.qteDisponible)}</span>
        },
      },
    ]
    if (voitLesCouts) {
      base.push(
        { id: 'cump', header: 'CUMP', accessorKey: 'cump', cell: ({ getValue }) => <span className="block text-right tabular-nums">{mga(getValue<number | null>())}</span> },
        { id: 'valeur', header: 'Valeur', accessorKey: 'valeur', cell: ({ getValue }) => <span className="block text-right font-medium tabular-nums">{mga(getValue<number | null>())}</span> },
      )
    }
    return base
  }, [voitLesCouts])

  const total = (stock.data ?? []).reduce((s, l) => s + (l.valeur ?? 0), 0)

  return (
    <Page>
      <PageHeader
        title="État du stock"
        subtitle="Quatre quantités par article et par dépôt. Disponible = physique − réservé."
        actions={
          <Button variant="secondary" onClick={() => void api.telecharger('/stock/export', 'stock.xlsx', { params: { depotId } })}>
            <Download size={16} /> Exporter
          </Button>
        }
      />
      <Card padding="sm" className="mb-4">
        <div className="grid gap-3 md:grid-cols-[1fr_220px_auto] md:items-end">
          <SearchField value={q} onChange={setQ} placeholder="Nom, SKU, référence ou code-barres…" />
          <Select value={depotId} onChange={(e) => setDepotId(e.target.value)} aria-label="Dépôt">
            <option value="">Tous les dépôts</option>
            {depots.data?.map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
          <label className="flex h-10 items-center gap-2 text-sm">
            <input type="checkbox" checked={avecStock} onChange={(e) => setAvecStock(e.target.checked)} className="h-4 w-4 accent-[var(--primary)]" />
            Avec stock seulement
          </label>
        </div>
      </Card>
      <DataTable
        tableId="etat-stock"
        columns={colonnes}
        data={stock.data ?? []}
        loading={stock.isLoading}
        error={stock.isError ? stock.error : null}
        onRetry={() => void stock.refetch()}
        empty="Aucun article ne correspond."
      />
      {voitLesCouts && (stock.data?.length ?? 0) > 0 && (
        <p className="mt-3 text-right text-sm text-muted-foreground">
          Valeur totale affichée : <span className="font-semibold text-foreground">{mga(total)}</span>
        </p>
      )}
    </Page>
  )
}

function Num({ v, gras }: { v: number; gras?: boolean }) {
  return <span className={`block text-right tabular-nums ${gras ? 'font-semibold' : 'text-muted-foreground'}`}>{qte(v)}</span>
}
