'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ClipboardPlus } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/input'
import { Modal } from '@/components/ui/modal'
import { QueryState } from '@/components/ui/query-state'
import { StatusChip } from '@/components/ui/status-chip'
import { STATUT_INVENTAIRE } from '@/components/stock/statuts'
import { api } from '@/lib/api/client'
import type { Inventaire } from '@/lib/api/types'
import { useCategories, useDepots } from '@/lib/api/hooks'
import { jour, mga } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

export default function Inventaires() {
  const { aLeRole, voitLesCouts } = useSession()
  const [ouverture, setOuverture] = useState(false)
  const inventaires = useQuery({ queryKey: ['inventaires'], queryFn: () => api.get<Inventaire[]>('/inventaires') })

  return (
    <Page>
      <PageHeader
        title="Inventaires"
        subtitle="Comptage complet ou tournant, saisie par scan, écarts validés par l'administrateur."
        actions={aLeRole('ADMIN', 'MAGASINIER') && <Button onClick={() => setOuverture(true)}><ClipboardPlus size={16} /> Ouvrir un inventaire</Button>}
      />
      <QueryState loading={inventaires.isLoading} error={inventaires.error} empty={inventaires.data?.length === 0} emptyLabel="Aucun inventaire pour l'instant." onRetry={() => void inventaires.refetch()}>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {inventaires.data?.map((i) => (
            <Link key={i.id} href={`/inventaires/${i.id}`}>
              <Card className="h-full transition-colors hover:border-primary/40">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-mono text-sm font-semibold">{i.numero}</p>
                    <p className="text-sm text-muted-foreground">{i.depotNom} · {i.type === 'COMPLET' ? 'complet' : 'tournant'}</p>
                  </div>
                  <StatusChip status={i.statut} styles={STATUT_INVENTAIRE} />
                </div>
                <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
                  <Chiffre label="Articles" valeur={i.nbLignes} />
                  <Chiffre label="Comptés" valeur={i.nbComptees} />
                  <Chiffre label="Écarts" valeur={i.nbEcarts} />
                </div>
                {voitLesCouts && i.nbEcarts > 0 && (
                  <p className="mt-3 text-xs text-muted-foreground">
                    Écarts : <span className="text-success">{mga(i.valeurEcartPositif)}</span> / <span className="text-destructive">{mga(i.valeurEcartNegatif)}</span>
                  </p>
                )}
                <p className="mt-2 text-2xs text-muted-foreground">Ouvert le {jour(i.createdAt)} par {i.createdBy}</p>
              </Card>
            </Link>
          ))}
        </div>
      </QueryState>
      <OuvertureModal open={ouverture} onClose={() => setOuverture(false)} />
    </Page>
  )
}

function Chiffre({ label, valeur }: { label: string; valeur: number }) {
  return (
    <div className="rounded-lg bg-muted px-2 py-1.5">
      <p className="text-base font-semibold tabular-nums">{valeur}</p>
      <p className="text-muted-foreground">{label}</p>
    </div>
  )
}

function OuvertureModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter()
  const depots = useDepots()
  const categories = useCategories()
  const [depotId, setDepotId] = useState('')
  const [type, setType] = useState<'COMPLET' | 'TOURNANT'>('COMPLET')
  const [categorieId, setCategorieId] = useState('')
  const [commentaire, setCommentaire] = useState('')
  const mutation = useMutation({
    mutationFn: () => api.post<Inventaire>('/inventaires', {
      depotId: Number(depotId), type, commentaire,
      categorieId: type === 'TOURNANT' && categorieId ? Number(categorieId) : null,
    }),
    onSuccess: (i) => router.push(`/inventaires/${i.id}`),
  })
  return (
    <Modal open={open} onClose={onClose} title="Ouvrir un inventaire">
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
        <Select label="Dépôt" value={depotId} onChange={(e) => setDepotId(e.target.value)} required>
          <option value="">— Choisir —</option>
          {depots.data?.filter((d) => d.actif).map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
        </Select>
        <div className="grid grid-cols-2 gap-2">
          {(['COMPLET', 'TOURNANT'] as const).map((t) => (
            <label key={t} className={`cursor-pointer rounded-lg border p-3 text-sm ${type === t ? 'border-primary bg-primary/5' : 'border-border'}`}>
              <input type="radio" name="type" value={t} checked={type === t} onChange={() => setType(t)} className="sr-only" />
              <span className="font-medium">{t === 'COMPLET' ? 'Complet' : 'Tournant'}</span>
              <span className="block text-xs text-muted-foreground">
                {t === 'COMPLET' ? 'Tout le stock du dépôt.' : 'Une catégorie, ou les articles scannés.'}
              </span>
            </label>
          ))}
        </div>
        {type === 'TOURNANT' && (
          <Select label="Catégorie (facultatif : sinon, ajoutez les articles en les scannant)" value={categorieId} onChange={(e) => setCategorieId(e.target.value)}>
            <option value="">— Aucune —</option>
            {categories.data?.filter((c) => c.actif).map((c) => <option key={c.id} value={c.id}>{c.nom}</option>)}
          </Select>
        )}
        <Input label="Commentaire" value={commentaire} onChange={(e) => setCommentaire(e.target.value)} />
        <p className="text-xs text-muted-foreground">
          Le stock théorique est photographié à l&apos;ouverture ; les écarts s&apos;appliqueront en delta à la validation.
        </p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>Ouvrir</Button>
        </div>
      </form>
    </Modal>
  )
}
