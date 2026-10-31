'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/input'
import { useToast } from '@/components/ui/toast'
import { LignesEditor, versApi, type LigneSaisie } from '@/components/stock/lignes-editor'
import { api } from '@/lib/api/client'
import type { DocumentMouvement, TypeMouvement } from '@/lib/api/types'
import { useDepots, useTypesMouvement } from '@/lib/api/hooks'
import { useSession } from '@/providers/session-provider'

export function MouvementModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { voitLesCouts } = useSession()
  const depots = useDepots()
  const types = useTypesMouvement()
  const toast = useToast()
  const qc = useQueryClient()
  const [type, setType] = useState<TypeMouvement>('ENTREE')
  const [depotId, setDepotId] = useState('')
  const [motif, setMotif] = useState('')
  const [lignes, setLignes] = useState<LigneSaisie[]>([])
  const [erreur, setErreur] = useState<string | null>(null)

  const info = types.data?.find((t) => t.code === type)
  const entree = (info?.sens ?? 1) > 0
  const avecCout = voitLesCouts && entree

  const mutation = useMutation({
    mutationFn: (corps: unknown) => api.post<DocumentMouvement>('/stock/mouvements', corps),
    onSuccess: (doc) => {
      toast.success(`Mouvement ${doc.numero} enregistré.`)
      void qc.invalidateQueries({ queryKey: ['stock'] })
      void qc.invalidateQueries({ queryKey: ['mouvements'] })
      void qc.invalidateQueries({ queryKey: ['alertes'] })
      setLignes([])
      setMotif('')
      onClose()
    },
  })

  function enregistrer(e: React.FormEvent) {
    e.preventDefault()
    setErreur(null)
    if (!depotId) return setErreur('Choisissez un dépôt.')
    if (lignes.length === 0) return setErreur('Ajoutez au moins un article.')
    const l = versApi(lignes, avecCout)
    if (!l) return setErreur('Chaque ligne doit avoir une quantité strictement positive.')
    if (info?.motifObligatoire && !motif.trim()) return setErreur(`Un motif est obligatoire pour « ${info.label} ».`)
    mutation.mutate({ type, depotId: Number(depotId), motif: motif || null, lignes: l })
  }

  return (
    <Modal open={open} onClose={onClose} title="Nouveau mouvement" size="lg">
      <form onSubmit={enregistrer} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Select label="Type" value={type} onChange={(e) => setType(e.target.value as TypeMouvement)}>
            {types.data?.filter((t) => t.saisieDirecte && t.code !== 'STOCK_INITIAL').map((t) => (
              <option key={t.code} value={t.code}>{t.sens > 0 ? '＋' : '－'} {t.label}</option>
            ))}
          </Select>
          <Select label="Dépôt" value={depotId} onChange={(e) => setDepotId(e.target.value)} required>
            <option value="">— Choisir —</option>
            {depots.data?.filter((d) => d.actif).map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
        </div>
        <Input
          label={info?.motifObligatoire ? 'Motif (obligatoire)' : 'Motif'}
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          placeholder={entree ? 'Ex. : réception hors import, retour fournisseur…' : 'Ex. : vente comptoir, échantillon…'}
        />
        {avecCout && (
          <p className="text-xs text-muted-foreground">
            Coût unitaire en ariary : laissez vide pour entrer au coût moyen actuel. Il recalcule le CUMP.
          </p>
        )}
        <LignesEditor lignes={lignes} onChange={setLignes} avecCout={avecCout} />
        {erreur && <p className="rounded-lg bg-destructive/5 px-3 py-2 text-sm text-destructive">{erreur}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>Enregistrer</Button>
        </div>
      </form>
    </Modal>
  )
}

export function TransfertModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const depots = useDepots()
  const toast = useToast()
  const qc = useQueryClient()
  const [source, setSource] = useState('')
  const [destination, setDestination] = useState('')
  const [motif, setMotif] = useState('')
  const [lignes, setLignes] = useState<LigneSaisie[]>([])
  const [erreur, setErreur] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (corps: unknown) => api.post<DocumentMouvement>('/stock/transferts', corps),
    onSuccess: (doc) => {
      toast.success(`Transfert ${doc.numero} enregistré.`)
      void qc.invalidateQueries({ queryKey: ['stock'] })
      void qc.invalidateQueries({ queryKey: ['mouvements'] })
      void qc.invalidateQueries({ queryKey: ['alertes'] })
      setLignes([])
      setMotif('')
      onClose()
    },
  })

  function enregistrer(e: React.FormEvent) {
    e.preventDefault()
    setErreur(null)
    if (!source || !destination) return setErreur('Choisissez les deux dépôts.')
    if (source === destination) return setErreur('Les dépôts de départ et d\'arrivée doivent être différents.')
    const l = versApi(lignes, false)
    if (lignes.length === 0 || !l) return setErreur('Ajoutez au moins un article avec une quantité positive.')
    mutation.mutate({ depotSourceId: Number(source), depotDestinationId: Number(destination), motif: motif || null, lignes: l })
  }

  const actifs = depots.data?.filter((d) => d.actif) ?? []
  return (
    <Modal open={open} onClose={onClose} title="Transfert entre dépôts" size="lg">
      <form onSubmit={enregistrer} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Select label="Dépôt de départ" value={source} onChange={(e) => setSource(e.target.value)} required>
            <option value="">— Choisir —</option>
            {actifs.map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
          <Select label="Dépôt d'arrivée" value={destination} onChange={(e) => setDestination(e.target.value)} required>
            <option value="">— Choisir —</option>
            {actifs.map((d) => <option key={d.id} value={d.id}>{d.nom}</option>)}
          </Select>
        </div>
        <Input label="Motif" value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Ex. : réassort boutique" />
        <LignesEditor lignes={lignes} onChange={setLignes} avecCout={false} />
        {erreur && <p className="rounded-lg bg-destructive/5 px-3 py-2 text-sm text-destructive">{erreur}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>Transférer</Button>
        </div>
      </form>
    </Modal>
  )
}
