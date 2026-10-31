'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useConfirm } from '@/components/ui/confirm'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { Categorie } from '@/lib/api/types'
import { useCategories } from '@/lib/api/hooks'

export function CategoriesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const categories = useCategories()
  const qc = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const [nom, setNom] = useState('')
  const [edition, setEdition] = useState<Categorie | null>(null)

  const rafraichir = () => void qc.invalidateQueries({ queryKey: ['categories'] })
  const enregistrer = useMutation({
    mutationFn: () => edition
      ? api.put(`/catalogue/categories/${edition.id}`, { nom, parentId: edition.parentId, actif: edition.actif })
      : api.post('/catalogue/categories', { nom }),
    onSuccess: () => { toast.success(edition ? 'Catégorie renommée.' : 'Catégorie créée.'); setNom(''); setEdition(null); rafraichir() },
  })
  const basculer = useMutation({
    mutationFn: (c: Categorie) => api.put(`/catalogue/categories/${c.id}`, { nom: c.nom, parentId: c.parentId, actif: !c.actif }),
    onSuccess: rafraichir,
  })
  const supprimer = useMutation({
    mutationFn: (c: Categorie) => api.delete(`/catalogue/categories/${c.id}`),
    onSuccess: () => { toast.success('Catégorie supprimée.'); rafraichir() },
  })

  return (
    <Modal open={open} onClose={onClose} title="Catégories" size="md">
      <form onSubmit={(e) => { e.preventDefault(); enregistrer.mutate() }} className="mb-4 flex items-end gap-2">
        <div className="flex-1">
          <Input label={edition ? `Renommer « ${edition.nom} »` : 'Nouvelle catégorie'} value={nom} onChange={(e) => setNom(e.target.value)} required />
        </div>
        <Button type="submit" loading={enregistrer.isPending}>{edition ? 'Renommer' : <><Plus size={14} /> Ajouter</>}</Button>
        {edition && <Button type="button" variant="ghost" onClick={() => { setEdition(null); setNom('') }}>Annuler</Button>}
      </form>
      <ul className="max-h-[50vh] divide-y divide-border overflow-y-auto rounded-lg border border-border">
        {categories.data?.length === 0 && <li className="px-4 py-3 text-sm text-muted-foreground">Aucune catégorie.</li>}
        {categories.data?.map((c) => (
          <li key={c.id} className="flex items-center gap-3 px-4 py-2.5 text-sm">
            <span className={`flex-1 ${c.actif ? '' : 'text-muted-foreground line-through'}`}>{c.nom}</span>
            <span className="text-xs text-muted-foreground">{c.nbProduits} produit{c.nbProduits > 1 ? 's' : ''}</span>
            <button type="button" className="text-xs text-muted-foreground hover:text-foreground" onClick={() => basculer.mutate(c)}>
              {c.actif ? 'Désactiver' : 'Réactiver'}
            </button>
            <button type="button" className="rounded p-1 text-muted-foreground hover:text-foreground" onClick={() => { setEdition(c); setNom(c.nom) }} aria-label={`Renommer ${c.nom}`}>
              <Pencil size={14} />
            </button>
            <button
              type="button"
              className="rounded p-1 text-muted-foreground hover:text-destructive"
              aria-label={`Supprimer ${c.nom}`}
              onClick={async () => {
                if (await confirm({ title: 'Supprimer la catégorie ?', message: `« ${c.nom} » sera supprimée.`, danger: true })) supprimer.mutate(c)
              }}
            >
              <Trash2 size={14} />
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
