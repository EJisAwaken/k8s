'use client'

import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { ArrowLeftRight, Download, Plus, Undo2 } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/input'
import { DataTable } from '@/components/ui/data-table'
import { Pagination } from '@/components/ui/pagination'
import { Badge } from '@/components/ui/badge'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { MouvementModal, TransfertModal } from '@/components/stock/mouvement-modal'
import { api } from '@/lib/api/client'
import type { DocumentMouvement, Mouvement, PageDto, TypeMouvement } from '@/lib/api/types'
import { useDepots, useTypesMouvement } from '@/lib/api/hooks'
import { horodatage, mga, qte } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

const TAILLE = 50

export default function JournalMouvements() {
  const { voitLesCouts, aLeRole } = useSession()
  const depots = useDepots()
  const types = useTypesMouvement()
  const [filtres, setFiltres] = useState({ depotId: '', type: '', numero: '', du: '', au: '' })
  const [page, setPage] = useState(0)
  const [saisie, setSaisie] = useState(false)
  const [transfert, setTransfert] = useState(false)
  const [aAnnuler, setAAnnuler] = useState<Mouvement | null>(null)
  const peutSaisir = aLeRole('ADMIN', 'MAGASINIER')

  const journal = useQuery({
    queryKey: ['mouvements', filtres, page],
    queryFn: () => api.get<PageDto<Mouvement>>('/stock/mouvements', { ...filtres, page, size: TAILLE }),
    placeholderData: (prev) => prev,
  })

  function filtrer(patch: Partial<typeof filtres>) {
    setFiltres((f) => ({ ...f, ...patch }))
    setPage(0)
  }

  const colonnes = useMemo<ColumnDef<Mouvement>[]>(() => {
    const c: ColumnDef<Mouvement>[] = [
      { id: 'date', header: 'Date', accessorKey: 'createdAt', cell: ({ getValue }) => <span className="whitespace-nowrap text-xs">{horodatage(getValue<string>())}</span> },
      {
        id: 'document',
        header: 'Document',
        cell: ({ row }) => (
          <div>
            <p className="whitespace-nowrap font-mono text-xs">{row.original.numero}</p>
            <p className="text-2xs text-muted-foreground">{row.original.typeLabel}</p>
          </div>
        ),
      },
      {
        id: 'article',
        header: 'Article',
        cell: ({ row }) => (
          <div className="min-w-56">
            <p className="font-medium">{row.original.libelle}</p>
            <p className="text-2xs text-muted-foreground">
              {row.original.sku}
              {row.original.numerosSerie.length > 0 && ` · N° ${row.original.numerosSerie.join(', ')}`}
            </p>
          </div>
        ),
      },
      { id: 'depot', header: 'Dépôt', accessorKey: 'depotNom' },
      {
        id: 'quantite',
        header: 'Quantité',
        cell: ({ row }) => (
          <span className={`block text-right font-semibold tabular-nums ${row.original.sens > 0 ? 'text-success' : 'text-destructive'}`}>
            {row.original.sens > 0 ? '+' : '−'}{qte(row.original.quantite)}
          </span>
        ),
      },
      { id: 'apres', header: 'Stock après', accessorKey: 'stockApres', cell: ({ getValue }) => <span className="block text-right tabular-nums text-muted-foreground">{qte(getValue<number>())}</span> },
    ]
    if (voitLesCouts) {
      c.push({ id: 'valeur', header: 'Valeur', accessorKey: 'valeur', cell: ({ getValue }) => <span className="block text-right tabular-nums">{mga(getValue<number | null>())}</span> })
    }
    c.push(
      {
        id: 'motif',
        header: 'Motif / auteur',
        cell: ({ row }) => (
          <div className="max-w-56">
            {row.original.motif && <p className="truncate text-xs" title={row.original.motif}>{row.original.motif}</p>}
            <p className="text-2xs text-muted-foreground">{row.original.createdBy}</p>
          </div>
        ),
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => {
          const m = row.original
          if (m.annule) return <Badge variant="gray">Annulé</Badge>
          if (m.type === 'ANNULATION' || !aLeRole('ADMIN')) return null
          return (
            <Button size="sm" variant="ghost" className="whitespace-nowrap" onClick={() => setAAnnuler(m)} aria-label={`Annuler le mouvement ${m.numero}`}>
              <Undo2 size={14} /> Annuler
            </Button>
          )
        },
      },
    )
    return c
  }, [voitLesCouts, aLeRole])

  return (
    <Page>
      <PageHeader
        title="Mouvements de stock"
        subtitle="Journal immuable : une erreur se corrige par un mouvement inverse (RG-05)."
        actions={
          <>
            <Button variant="secondary" onClick={() => void api.telecharger('/stock/mouvements/export', 'mouvements.xlsx', { params: filtres })}>
              <Download size={16} /> Exporter
            </Button>
            {peutSaisir && (
              <>
                <Button variant="secondary" onClick={() => setTransfert(true)}><ArrowLeftRight size={16} /> Transfert</Button>
                <Button onClick={() => setSaisie(true)}><Plus size={16} /> Nouveau mouvement</Button>
              </>
            )}
          </>
        }
      />
      <Card padding="sm" className="mb-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Select label="Dépôt" value={filtres.depotId} onChange={(e) => filtrer({ depotId: e.target.value })}>
            <option value="">Tous</option>
            {depots.data?.map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
          <Select label="Type" value={filtres.type} onChange={(e) => filtrer({ type: e.target.value as TypeMouvement | '' })}>
            <option value="">Tous</option>
            {types.data?.map((t) => <option key={t.code} value={t.code}>{t.label}</option>)}
          </Select>
          <Input label="N° de document" value={filtres.numero} onChange={(e) => filtrer({ numero: e.target.value })} placeholder="TRF-2026-00001" />
          <Input label="Du" type="date" value={filtres.du} onChange={(e) => filtrer({ du: e.target.value })} />
          <Input label="Au" type="date" value={filtres.au} onChange={(e) => filtrer({ au: e.target.value })} />
        </div>
      </Card>
      <DataTable
        tableId="mouvements"
        columns={colonnes}
        data={journal.data?.content ?? []}
        loading={journal.isLoading}
        error={journal.isError ? journal.error : null}
        onRetry={() => void journal.refetch()}
        empty="Aucun mouvement sur ces critères."
      />
      {journal.data && (
        <Pagination
          page={journal.data.page}
          totalPages={journal.data.totalPages}
          totalElements={journal.data.totalElements}
          size={journal.data.size}
          onPageChange={setPage}
          label="mouvement"
          labelPlural="mouvements"
        />
      )}
      <MouvementModal open={saisie} onClose={() => setSaisie(false)} />
      <TransfertModal open={transfert} onClose={() => setTransfert(false)} />
      <AnnulationModal mouvement={aAnnuler} onClose={() => setAAnnuler(null)} />
    </Page>
  )
}

function AnnulationModal({ mouvement, onClose }: { mouvement: Mouvement | null; onClose: () => void }) {
  const [motif, setMotif] = useState('')
  const toast = useToast()
  const qc = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => api.post<DocumentMouvement>(`/stock/mouvements/${mouvement!.id}/annulation`, { motif }),
    onSuccess: (doc) => {
      toast.success(`Annulation ${doc.numero} enregistrée.`)
      void qc.invalidateQueries({ queryKey: ['mouvements'] })
      void qc.invalidateQueries({ queryKey: ['stock'] })
      void qc.invalidateQueries({ queryKey: ['alertes'] })
      setMotif('')
      onClose()
    },
  })
  return (
    <Modal open={mouvement !== null} onClose={onClose} title="Annuler un mouvement">
      {mouvement && (
        <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Un mouvement inverse sera créé pour <span className="font-medium text-foreground">{mouvement.typeLabel.toLowerCase()} de {qte(mouvement.quantite)} × {mouvement.libelle}</span> ({mouvement.numero}).
            {mouvement.documentType === 'TRANSFERT' && ' Les deux jambes du transfert sont annulées ensemble.'} Le mouvement d&apos;origine reste dans le journal.
          </p>
          <Input label="Motif (obligatoire)" value={motif} onChange={(e) => setMotif(e.target.value)} required autoFocus />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>Fermer</Button>
            <Button type="submit" variant="danger" loading={mutation.isPending} disabled={!motif.trim()}>Annuler le mouvement</Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
