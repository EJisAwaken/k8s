'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { FolderTree, Plus } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/input'
import { SearchField, useDebouncedValue } from '@/components/ui/search-field'
import { DataTable } from '@/components/ui/data-table'
import { Pagination } from '@/components/ui/pagination'
import { Badge } from '@/components/ui/badge'
import { ProduitModal } from '@/components/catalogue/produit-modal'
import { CategoriesModal } from '@/components/catalogue/categories-modal'
import { api } from '@/lib/api/client'
import type { PageDto, Produit } from '@/lib/api/types'
import { useCategories } from '@/lib/api/hooks'
import { mga, qte } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

export default function Catalogue() {
  const router = useRouter()
  const { aLeRole } = useSession()
  const peutEcrire = aLeRole('ADMIN', 'ACHATS')
  const categories = useCategories()
  const [q, setQ] = useState('')
  const [categorieId, setCategorieId] = useState('')
  const [archive, setArchive] = useState(false)
  const [page, setPage] = useState(0)
  const [creation, setCreation] = useState(false)
  const [gestionCategories, setGestionCategories] = useState(false)
  const recherche = useDebouncedValue(q, 300)

  const produits = useQuery({
    queryKey: ['produits', recherche, categorieId, archive, page],
    queryFn: () => api.get<PageDto<Produit>>('/catalogue/produits', { q: recherche, categorieId, archive, page, size: 25 }),
    placeholderData: (prev) => prev,
  })

  const colonnes = useMemo<ColumnDef<Produit>[]>(() => [
    {
      id: 'produit',
      header: 'Produit',
      cell: ({ row }) => (
        <Link href={`/catalogue/${row.original.id}`} className="group block min-w-0">
          <p className="font-medium group-hover:text-primary group-hover:underline">{row.original.nom}</p>
          <p className="text-2xs text-muted-foreground">
            {row.original.reference}{row.original.nomFournisseur ? ` · ${row.original.nomFournisseur}` : ''}
          </p>
        </Link>
      ),
    },
    { id: 'categorie', header: 'Catégorie', accessorFn: (p) => p.categorieNom ?? '—' },
    {
      id: 'variantes',
      header: 'Variantes',
      cell: ({ row }) => (
        <div className="flex flex-wrap items-center gap-1">
          <span className="text-sm">{row.original.variantes.length}</span>
          {row.original.suiviSerie && <Badge variant="blue">N° série</Badge>}
        </div>
      ),
    },
    {
      id: 'prix',
      header: 'Prix détail',
      cell: ({ row }) => {
        const prix = row.original.variantes.map((v) => v.prixDetail).filter((p): p is number => p != null)
        if (prix.length === 0) return <span className="text-muted-foreground">—</span>
        const min = Math.min(...prix)
        const max = Math.max(...prix)
        return <span className="tabular-nums">{min === max ? mga(min) : `${mga(min)} – ${mga(max)}`}</span>
      },
    },
    {
      id: 'stock',
      header: 'Disponible',
      cell: ({ row }) => (
        <span className="block text-right font-semibold tabular-nums">
          {qte(row.original.variantes.reduce((s, v) => s + v.qteDisponible, 0))}
        </span>
      ),
    },
  ], [])

  return (
    <Page>
      <PageHeader
        title="Catalogue"
        subtitle="Produits, variantes, codes-barres et grilles de prix."
        actions={peutEcrire && (
          <>
            <Button variant="secondary" onClick={() => setGestionCategories(true)}><FolderTree size={16} /> Catégories</Button>
            <Button onClick={() => setCreation(true)}><Plus size={16} /> Nouveau produit</Button>
          </>
        )}
      />
      <Card padding="sm" className="mb-4">
        <div className="grid gap-3 md:grid-cols-[1fr_220px_auto] md:items-end">
          <SearchField value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Nom, référence, SKU, code-barres, nom fournisseur…" />
          <Select value={categorieId} onChange={(e) => { setCategorieId(e.target.value); setPage(0) }} aria-label="Catégorie">
            <option value="">Toutes les catégories</option>
            {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.nom}</option>)}
          </Select>
          <label className="flex h-10 items-center gap-2 text-sm">
            <input type="checkbox" checked={archive} onChange={(e) => { setArchive(e.target.checked); setPage(0) }} className="h-4 w-4 accent-[var(--primary)]" />
            Produits archivés
          </label>
        </div>
      </Card>
      <DataTable
        tableId="catalogue"
        columns={colonnes}
        data={produits.data?.content ?? []}
        loading={produits.isLoading}
        error={produits.isError ? produits.error : null}
        onRetry={() => void produits.refetch()}
        empty={archive ? 'Aucun produit archivé.' : 'Aucun produit. Créez-en un ou importez votre catalogue.'}
        emptyAction={peutEcrire && !archive ? <Button size="sm" onClick={() => setCreation(true)}><Plus size={14} /> Nouveau produit</Button> : undefined}
      />
      {produits.data && (
        <Pagination page={produits.data.page} totalPages={produits.data.totalPages} totalElements={produits.data.totalElements}
          size={produits.data.size} onPageChange={setPage} label="produit" labelPlural="produits" />
      )}
      <ProduitModal open={creation} onClose={() => setCreation(false)} onSaved={(p) => router.push(`/catalogue/${p.id}`)} />
      <CategoriesModal open={gestionCategories} onClose={() => setGestionCategories(false)} />
    </Page>
  )
}
