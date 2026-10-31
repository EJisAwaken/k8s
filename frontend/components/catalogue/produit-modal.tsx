'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Input, Select, Textarea } from '@/components/ui/input'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { Produit, VarianteForm } from '@/lib/api/types'
import { useCategories } from '@/lib/api/hooks'
import { useSession } from '@/providers/session-provider'

const VIDE = {
  reference: '', nom: '', nomFournisseur: '', referenceFournisseur: '', categorieId: '', unite: 'pièce',
  poidsKg: '', volumeM3: '', codeSh: '', suiviSerie: false, description: '',
}

export const varianteVide = (sku = ''): VarianteForm => ({
  sku, attributs: '', codeBarres: '', prixDetail: null, prixGros: null, prixRevendeur: null, seuilGros: null, seuilMini: 0,
})

const num = (s: string) => (s.trim() === '' ? null : Number(s))

interface Props {
  open: boolean
  onClose: () => void
  produit?: Produit | null
  onSaved?: (p: Produit) => void
}

/** Création (avec ses variantes) ou modification de la fiche produit. */
export function ProduitModal(props: Props) {
  return (
    <Modal open={props.open} onClose={props.onClose} title={props.produit ? `Modifier ${props.produit.reference}` : 'Nouveau produit'} size="xl">
      {props.open && <ProduitForm key={props.produit?.id ?? 'nouveau'} {...props} />}
    </Modal>
  )
}

function initial(produit?: Produit | null) {
  return produit ? {
    reference: produit.reference, nom: produit.nom, nomFournisseur: produit.nomFournisseur ?? '',
    referenceFournisseur: produit.referenceFournisseur ?? '', categorieId: produit.categorieId ? String(produit.categorieId) : '',
    unite: produit.unite, poidsKg: produit.poidsKg?.toString() ?? '', volumeM3: produit.volumeM3?.toString() ?? '',
    codeSh: produit.codeSh ?? '', suiviSerie: produit.suiviSerie, description: produit.description ?? '',
  } : VIDE
}

function ProduitForm({ onClose, produit, onSaved }: Props) {
  const categories = useCategories()
  const toast = useToast()
  const qc = useQueryClient()
  const { aLeRole } = useSession()
  const prixModifiables = aLeRole('ADMIN')
  const [f, setF] = useState(() => initial(produit))
  const [variantes, setVariantes] = useState<VarianteForm[]>([])

  const mutation = useMutation({
    mutationFn: () => {
      const corps = {
        reference: f.reference, nom: f.nom, nomFournisseur: f.nomFournisseur, referenceFournisseur: f.referenceFournisseur,
        categorieId: f.categorieId ? Number(f.categorieId) : null, unite: f.unite, poidsKg: num(f.poidsKg),
        volumeM3: num(f.volumeM3), codeSh: f.codeSh, suiviSerie: f.suiviSerie, description: f.description,
        variantes: produit ? undefined : variantes.map((v) => ({ ...v, codeBarres: v.codeBarres || null })),
      }
      return produit ? api.put<Produit>(`/catalogue/produits/${produit.id}`, corps) : api.post<Produit>('/catalogue/produits', corps)
    },
    onSuccess: (p) => {
      toast.success(produit ? 'Produit modifié.' : `Produit ${p.reference} créé.`)
      void qc.invalidateQueries({ queryKey: ['produits'] })
      void qc.invalidateQueries({ queryKey: ['produit', p.id] })
      void qc.invalidateQueries({ queryKey: ['categories'] })
      onSaved?.(p)
      onClose()
    },
  })

  const set = (k: keyof typeof VIDE) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setF((x) => ({ ...x, [k]: e.target.value }))

  return (
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Input label="Référence interne" value={f.reference} onChange={set('reference')} required maxLength={60} />
          <div className="lg:col-span-2"><Input label="Nom (français)" value={f.nom} onChange={set('nom')} required maxLength={200} /></div>
          <Input label="Nom chez le fournisseur" value={f.nomFournisseur} onChange={set('nomFournisseur')} placeholder="Nom chinois, nom 1688…" />
          <Input label="Référence fournisseur" value={f.referenceFournisseur} onChange={set('referenceFournisseur')} />
          <Select label="Catégorie" value={f.categorieId} onChange={set('categorieId')}>
            <option value="">— Aucune —</option>
            {categories.data?.filter((c) => c.actif || String(c.id) === f.categorieId).map((c) => <option key={c.id} value={c.id}>{c.nom}</option>)}
          </Select>
          <Input label="Unité" value={f.unite} onChange={set('unite')} placeholder="pièce, kg, m…" />
          <Input label="Poids unitaire (kg)" type="number" step="0.001" min="0" value={f.poidsKg} onChange={set('poidsKg')} />
          <Input label="Volume unitaire (m³)" type="number" step="0.000001" min="0" value={f.volumeM3} onChange={set('volumeM3')} />
          <Input label="Code SH douanier" value={f.codeSh} onChange={set('codeSh')} placeholder="8518.30" />
        </div>
        <label className="flex items-start gap-3 rounded-lg border border-border p-3 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 accent-[var(--primary)]"
            checked={f.suiviSerie}
            disabled={produit?.aDesMouvements}
            onChange={(e) => setF((x) => ({ ...x, suiviSerie: e.target.checked }))}
          />
          <span>
            <span className="font-medium">Suivi par numéro de série / IMEI</span>
            <span className="block text-xs text-muted-foreground">
              Téléphones, électronique : chaque unité est tracée pour la garantie.
              {produit?.aDesMouvements && ' Ne peut plus changer : le produit a des mouvements.'}
            </span>
          </span>
        </label>
        <Textarea label="Description" rows={2} value={f.description} onChange={set('description')} />

        {!produit && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">Variantes <span className="font-normal text-muted-foreground">— couleur, taille, modèle ; vide = variante unique</span></h3>
              <Button type="button" size="sm" variant="secondary" onClick={() => setVariantes((v) => [...v, varianteVide(f.reference ? `${f.reference}-${v.length + 1}` : '')])}>
                <Plus size={14} /> Ajouter
              </Button>
            </div>
            {variantes.map((v, i) => (
              <div key={i} className="grid gap-2 rounded-lg border border-border p-3 sm:grid-cols-[1fr_1.4fr_1fr_1fr_auto] sm:items-end">
                <Input label="SKU" value={v.sku} required onChange={(e) => setVariantes((all) => all.map((x, j) => (j === i ? { ...x, sku: e.target.value } : x)))} />
                <Input label="Attributs" value={v.attributs} placeholder="Couleur : noir" onChange={(e) => setVariantes((all) => all.map((x, j) => (j === i ? { ...x, attributs: e.target.value } : x)))} />
                <Input label="Prix détail (Ar)" type="number" min="0" disabled={!prixModifiables} value={v.prixDetail ?? ''} onChange={(e) => setVariantes((all) => all.map((x, j) => (j === i ? { ...x, prixDetail: num(e.target.value) } : x)))} />
                <Input label="Seuil mini" type="number" min="0" value={v.seuilMini ?? ''} onChange={(e) => setVariantes((all) => all.map((x, j) => (j === i ? { ...x, seuilMini: num(e.target.value) } : x)))} />
                <button type="button" onClick={() => setVariantes((all) => all.filter((_, j) => j !== i))} className="mb-1 rounded-md p-2 text-muted-foreground hover:bg-destructive/5 hover:text-destructive" aria-label="Retirer la variante">
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {!prixModifiables && <p className="text-xs text-muted-foreground">Les prix de vente sont fixés par l&apos;administrateur.</p>}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>{produit ? 'Enregistrer' : 'Créer le produit'}</Button>
        </div>
      </form>
  )
}
