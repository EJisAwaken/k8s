'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MapPin, Pencil, Plus } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Modal } from '@/components/ui/modal'
import { Badge } from '@/components/ui/badge'
import { QueryState } from '@/components/ui/query-state'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { Depot, Emplacement } from '@/lib/api/types'
import { useDepots } from '@/lib/api/hooks'
import { useSession } from '@/providers/session-provider'

export default function Depots() {
  const { aLeRole } = useSession()
  const depots = useDepots()
  const [edition, setEdition] = useState<Depot | null | undefined>(undefined)
  const [selection, setSelection] = useState<Depot | null>(null)

  return (
    <Page>
      <PageHeader
        title="Dépôts"
        subtitle="Dépôts et boutiques, avec leurs emplacements (rayon, étagère)."
        actions={aLeRole('ADMIN') && <Button onClick={() => setEdition(null)}><Plus size={16} /> Nouveau dépôt</Button>}
      />
      <QueryState loading={depots.isLoading} error={depots.error} empty={depots.data?.length === 0} emptyLabel="Aucun dépôt. Créez-en un pour commencer." onRetry={() => void depots.refetch()}>
        <div className="grid gap-4 lg:grid-cols-[1fr_1.2fr]">
          <ul className="space-y-2">
            {depots.data?.map((d) => (
              <li key={d.id}>
                <Card
                  padding="sm"
                  className={`cursor-pointer transition-colors ${selection?.id === d.id ? 'border-primary bg-primary/5' : 'hover:border-primary/40'}`}
                  onClick={() => setSelection(d)}
                >
                  <div className="flex items-center gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="font-medium">{d.nom} <span className="ml-1 font-mono text-xs text-muted-foreground">{d.code}</span></p>
                      {d.adresse && <p className="text-xs text-muted-foreground">{d.adresse}</p>}
                    </div>
                    {!d.actif && <Badge variant="gray">Désactivé</Badge>}
                    {aLeRole('ADMIN') && (
                      <button type="button" onClick={(e) => { e.stopPropagation(); setEdition(d) }} className="rounded p-1.5 text-muted-foreground hover:text-foreground" aria-label={`Modifier ${d.nom}`}>
                        <Pencil size={14} />
                      </button>
                    )}
                  </div>
                </Card>
              </li>
            ))}
          </ul>
          {selection ? <Emplacements depot={selection} /> : (
            <Card className="flex items-center justify-center text-sm text-muted-foreground">Sélectionnez un dépôt pour voir ses emplacements.</Card>
          )}
        </div>
      </QueryState>
      <DepotModal depot={edition} onClose={() => setEdition(undefined)} />
    </Page>
  )
}

function DepotModal({ depot, onClose }: { depot: Depot | null | undefined; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState({ code: '', nom: '', adresse: '', actif: true })
  const [ouvert, setOuvert] = useState<Depot | null | undefined>(undefined)
  if (depot !== ouvert) {
    setOuvert(depot)
    setF(depot ? { code: depot.code, nom: depot.nom, adresse: depot.adresse ?? '', actif: depot.actif } : { code: '', nom: '', adresse: '', actif: true })
  }
  const mutation = useMutation({
    mutationFn: () => (depot ? api.put(`/depots/${depot.id}`, f) : api.post('/depots', f)),
    onSuccess: () => { toast.success('Dépôt enregistré.'); void qc.invalidateQueries({ queryKey: ['depots'] }); onClose() },
  })
  return (
    <Modal open={depot !== undefined} onClose={onClose} title={depot ? `Modifier ${depot.nom}` : 'Nouveau dépôt'}>
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-[140px_1fr]">
          <Input label="Code" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value.toUpperCase() })} required maxLength={20} placeholder="PRINCIPAL" />
          <Input label="Nom" value={f.nom} onChange={(e) => setF({ ...f, nom: e.target.value })} required />
        </div>
        <Input label="Adresse" value={f.adresse} onChange={(e) => setF({ ...f, adresse: e.target.value })} />
        {depot && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={f.actif} onChange={(e) => setF({ ...f, actif: e.target.checked })} className="h-4 w-4 accent-[var(--primary)]" />
            Dépôt actif (un dépôt désactivé ne reçoit plus de mouvement)
          </label>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>Enregistrer</Button>
        </div>
      </form>
    </Modal>
  )
}

function Emplacements({ depot }: { depot: Depot }) {
  const { aLeRole } = useSession()
  const qc = useQueryClient()
  const [code, setCode] = useState('')
  const [libelle, setLibelle] = useState('')
  const cle = ['emplacements', depot.id]
  const emplacements = useQuery({ queryKey: cle, queryFn: () => api.get<Emplacement[]>(`/depots/${depot.id}/emplacements`) })
  const ajouter = useMutation({
    mutationFn: () => api.post(`/depots/${depot.id}/emplacements`, { code, libelle }),
    onSuccess: () => { setCode(''); setLibelle(''); void qc.invalidateQueries({ queryKey: cle }) },
  })
  const basculer = useMutation({
    mutationFn: (e: Emplacement) => api.put(`/depots/${depot.id}/emplacements/${e.id}`, { code: e.code, libelle: e.libelle, actif: !e.actif }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: cle }),
  })
  return (
    <Card padding="none">
      <h2 className="flex items-center gap-2 border-b border-border px-5 py-4 font-semibold"><MapPin size={16} /> Emplacements — {depot.nom}</h2>
      {aLeRole('ADMIN', 'MAGASINIER') && (
        <form onSubmit={(e) => { e.preventDefault(); ajouter.mutate() }} className="flex flex-wrap items-end gap-2 border-b border-border p-4">
          <div className="w-32"><Input label="Code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} required placeholder="A-01" /></div>
          <div className="min-w-40 flex-1"><Input label="Libellé" value={libelle} onChange={(e) => setLibelle(e.target.value)} placeholder="Rayon A, étagère 1" /></div>
          <Button type="submit" loading={ajouter.isPending}><Plus size={14} /> Ajouter</Button>
        </form>
      )}
      <ul className="divide-y divide-border">
        {emplacements.data?.length === 0 && <li className="px-5 py-4 text-sm text-muted-foreground">Aucun emplacement (facultatif).</li>}
        {emplacements.data?.map((e) => (
          <li key={e.id} className="flex items-center gap-3 px-5 py-2.5 text-sm">
            <span className="w-20 font-mono">{e.code}</span>
            <span className={`flex-1 ${e.actif ? '' : 'text-muted-foreground line-through'}`}>{e.libelle ?? '—'}</span>
            {aLeRole('ADMIN', 'MAGASINIER') && (
              <button type="button" className="text-xs text-muted-foreground hover:text-foreground" onClick={() => basculer.mutate(e)}>
                {e.actif ? 'Désactiver' : 'Réactiver'}
              </button>
            )}
          </li>
        ))}
      </ul>
    </Card>
  )
}
