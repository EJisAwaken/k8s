'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { Produit, Variante, VarianteForm } from '@/lib/api/types'
import { varianteVide } from '@/components/catalogue/produit-modal'
import { useSession } from '@/providers/session-provider'

const num = (s: string) => (s.trim() === '' ? null : Number(s))

interface Props {
  open: boolean
  onClose: () => void
  produit: Produit
  variante?: Variante | null
}

/** Ajout ou modification d'une variante : SKU, code-barres, grille de prix, seuils. */
export function VarianteModal(props: Props) {
  return (
    <Modal open={props.open} onClose={props.onClose} title={props.variante ? `Variante ${props.variante.sku}` : 'Nouvelle variante'} size="lg">
      {props.open && <VarianteForm_ key={props.variante?.id ?? 'nouvelle'} {...props} />}
    </Modal>
  )
}

function VarianteForm_({ onClose, produit, variante }: Props) {
  const toast = useToast()
  const qc = useQueryClient()
  const { aLeRole } = useSession()
  const prix = aLeRole('ADMIN')
  const [v, setV] = useState<VarianteForm>(() => variante ? {
    sku: variante.sku, attributs: variante.attributs ?? '', codeBarres: variante.codeBarres ?? '',
    prixDetail: variante.prixDetail, prixGros: variante.prixGros, prixRevendeur: variante.prixRevendeur,
    seuilGros: variante.seuilGros, seuilMini: variante.seuilMini,
  } : varianteVide(`${produit.reference}-${produit.variantes.length + 1}`))

  const mutation = useMutation({
    mutationFn: () => {
      const corps = { ...v, codeBarres: v.codeBarres || null }
      return variante
        ? api.put<Produit>(`/catalogue/variantes/${variante.id}`, corps)
        : api.post<Produit>(`/catalogue/produits/${produit.id}/variantes`, corps)
    },
    onSuccess: (p) => {
      toast.success(variante ? 'Variante modifiée.' : 'Variante ajoutée.')
      qc.setQueryData(['produit', p.id], p)
      void qc.invalidateQueries({ queryKey: ['produits'] })
      onClose()
    },
  })

  const champ = (k: keyof VarianteForm, numerique = false) => ({
    value: (v[k] ?? '') as string | number,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      setV((x) => ({ ...x, [k]: numerique ? num(e.target.value) : e.target.value })),
  })

  return (
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="SKU" required maxLength={60} {...champ('sku')} />
          <Input label="Attributs" placeholder="Couleur : noir · Taille : M" {...champ('attributs')} />
          <div className="sm:col-span-2"><Input label="Code-barres" placeholder="Vide → EAN-13 interne généré" {...champ('codeBarres')} /></div>
        </div>
        <fieldset className="space-y-2">
          <legend className="text-sm font-semibold">Grille de prix (ariary)</legend>
          <div className="grid gap-3 sm:grid-cols-3">
            <Input label="Détail" type="number" min="0" disabled={!prix} {...champ('prixDetail', true)} />
            <Input label="Gros" type="number" min="0" disabled={!prix} {...champ('prixGros', true)} />
            <Input label="Revendeur" type="number" min="0" disabled={!prix} {...champ('prixRevendeur', true)} />
          </div>
          {!prix && <p className="text-xs text-muted-foreground">Les prix de vente sont fixés par l&apos;administrateur.</p>}
        </fieldset>
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="Quantité mini pour le prix de gros" type="number" min="0" step="any" {...champ('seuilGros', true)} />
          <Input label="Seuil d'alerte (stock mini)" type="number" min="0" step="any" {...champ('seuilMini', true)} />
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>Enregistrer</Button>
        </div>
      </form>
  )
}
