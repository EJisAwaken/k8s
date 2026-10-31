'use client'

import Link from 'next/link'
import { use, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, ArrowLeft, Barcode, Pencil, Plus, Trash2 } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { QueryState } from '@/components/ui/query-state'
import { useConfirm } from '@/components/ui/confirm'
import { useToast } from '@/components/ui/toast'
import { ProduitModal } from '@/components/catalogue/produit-modal'
import { VarianteModal } from '@/components/catalogue/variante-modal'
import { api } from '@/lib/api/client'
import type { Produit, StockLigne, Variante } from '@/lib/api/types'
import { mga, qte } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

export default function FicheProduit({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const qc = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const { aLeRole, voitLesCouts } = useSession()
  const peutEcrire = aLeRole('ADMIN', 'ACHATS')
  const [edition, setEdition] = useState(false)
  const [variante, setVariante] = useState<Variante | null | undefined>(undefined)

  const produit = useQuery({ queryKey: ['produit', Number(id)], queryFn: () => api.get<Produit>(`/catalogue/produits/${id}`) })
  const p = produit.data

  const stocks = useQueries({
    queries: (p?.variantes ?? []).map((v) => ({
      queryKey: ['stock', 'variante', v.id],
      queryFn: () => api.get<StockLigne[]>('/stock', { varianteId: v.id }),
    })),
  })
  const lignesStock = stocks.flatMap((s) => s.data ?? [])

  const maj = (nouveau: Produit) => {
    qc.setQueryData(['produit', nouveau.id], nouveau)
    void qc.invalidateQueries({ queryKey: ['produits'] })
  }
  const archiver = useMutation({
    mutationFn: (archive: boolean) => api.post<Produit>(`/catalogue/produits/${id}/archive`, undefined, { archive }),
    onSuccess: (n) => { maj(n); toast.success(n.archive ? 'Produit archivé.' : 'Produit réactivé.') },
  })
  const supprimer = useMutation({
    mutationFn: () => api.delete(`/catalogue/produits/${id}`),
    onSuccess: () => { toast.success('Produit supprimé.'); void qc.invalidateQueries({ queryKey: ['produits'] }); router.push('/catalogue') },
  })
  const archiverVariante = useMutation({
    mutationFn: (v: Variante) => api.post<Produit>(`/catalogue/variantes/${v.id}/archive`, undefined, { archive: !v.archive }),
    onSuccess: maj,
  })
  const supprimerVariante = useMutation({
    mutationFn: (v: Variante) => api.delete<Produit>(`/catalogue/variantes/${v.id}`),
    onSuccess: (n) => { maj(n); toast.success('Variante supprimée.') },
  })

  return (
    <Page>
      <Link href="/catalogue" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft size={14} /> Catalogue
      </Link>
      <QueryState loading={produit.isLoading} error={produit.error} onRetry={() => void produit.refetch()}>
        {p && (
          <>
            <PageHeader
              title={p.nom}
              subtitle={
                <span className="flex flex-wrap items-center gap-2">
                  <span className="font-mono">{p.reference}</span>
                  {p.categorieNom && <Badge>{p.categorieNom}</Badge>}
                  {p.suiviSerie && <Badge variant="blue">Suivi n° de série</Badge>}
                  {p.archive && <Badge variant="yellow">Archivé</Badge>}
                </span>
              }
              actions={
                <>
                  <Link href={`/etiquettes?varianteIds=${p.variantes.map((v) => v.id).join(',')}`}>
                    <Button variant="secondary"><Barcode size={16} /> Étiquettes</Button>
                  </Link>
                  {peutEcrire && <Button variant="secondary" onClick={() => setEdition(true)}><Pencil size={16} /> Modifier</Button>}
                  {peutEcrire && (
                    <Button variant="secondary" loading={archiver.isPending} onClick={() => archiver.mutate(!p.archive)}>
                      {p.archive ? <><ArchiveRestore size={16} /> Réactiver</> : <><Archive size={16} /> Archiver</>}
                    </Button>
                  )}
                  {aLeRole('ADMIN') && !p.aDesMouvements && (
                    <Button
                      variant="danger"
                      onClick={async () => {
                        if (await confirm({ title: 'Supprimer ce produit ?', message: 'Il n\'a aucun mouvement : la suppression est définitive.', danger: true })) supprimer.mutate()
                      }}
                    >
                      <Trash2 size={16} /> Supprimer
                    </Button>
                  )}
                </>
              }
            />

            <div className="grid gap-4 lg:grid-cols-3">
              <Card className="lg:col-span-1">
                <h2 className="mb-3 font-semibold">Fiche</h2>
                <dl className="space-y-2 text-sm">
                  <Ligne label="Nom fournisseur" valeur={p.nomFournisseur} />
                  <Ligne label="Réf. fournisseur" valeur={p.referenceFournisseur} />
                  <Ligne label="Unité" valeur={p.unite} />
                  <Ligne label="Poids unitaire" valeur={p.poidsKg != null ? `${qte(p.poidsKg)} kg` : null} />
                  <Ligne label="Volume unitaire" valeur={p.volumeM3 != null ? `${qte(p.volumeM3)} m³` : null} />
                  <Ligne label="Code SH" valeur={p.codeSh} />
                </dl>
                {p.description && <p className="mt-3 whitespace-pre-line text-sm text-muted-foreground">{p.description}</p>}
                {p.aDesMouvements && (
                  <p className="mt-4 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
                    RG-01 : ce produit a des mouvements de stock, il ne peut plus être supprimé — seulement archivé.
                  </p>
                )}
              </Card>

              <Card padding="none" className="lg:col-span-2">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <h2 className="font-semibold">Variantes</h2>
                  {peutEcrire && <Button size="sm" variant="secondary" onClick={() => setVariante(null)}><Plus size={14} /> Ajouter</Button>}
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="px-4 py-2.5 font-medium">SKU / attributs</th>
                        <th className="px-4 py-2.5 font-medium">Code-barres</th>
                        <th className="px-4 py-2.5 text-right font-medium">Détail</th>
                        <th className="px-4 py-2.5 text-right font-medium">Gros</th>
                        <th className="px-4 py-2.5 text-right font-medium">Revendeur</th>
                        <th className="px-4 py-2.5 text-right font-medium">Dispo.</th>
                        <th className="px-4 py-2.5" />
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {p.variantes.map((v) => (
                        <tr key={v.id} className={v.archive ? 'opacity-60' : ''}>
                          <td className="px-4 py-3">
                            <p className="whitespace-nowrap font-medium">{v.sku} {v.archive && <Badge variant="yellow">Archivée</Badge>}</p>
                            <p className="text-2xs text-muted-foreground">{v.attributs ?? '—'}</p>
                          </td>
                          <td className="px-4 py-3 font-mono text-xs">{v.codeBarres}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{mga(v.prixDetail)}</td>
                          <td className="px-4 py-3 text-right tabular-nums">
                            {mga(v.prixGros)}
                            {v.seuilGros != null && <span className="block text-2xs text-muted-foreground">dès {qte(v.seuilGros)}</span>}
                          </td>
                          <td className="px-4 py-3 text-right tabular-nums">{mga(v.prixRevendeur)}</td>
                          <td className="px-4 py-3 text-right font-semibold tabular-nums">
                            {qte(v.qteDisponible)}
                            <span className="block text-2xs font-normal text-muted-foreground">seuil {qte(v.seuilMini)}</span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right">
                            {peutEcrire && (
                              <>
                                <button type="button" className="rounded p-1.5 text-muted-foreground hover:text-foreground" onClick={() => setVariante(v)} aria-label={`Modifier ${v.sku}`}><Pencil size={14} /></button>
                                <button type="button" className="rounded p-1.5 text-muted-foreground hover:text-foreground" onClick={() => archiverVariante.mutate(v)} aria-label={v.archive ? `Réactiver ${v.sku}` : `Archiver ${v.sku}`}>
                                  {v.archive ? <ArchiveRestore size={14} /> : <Archive size={14} />}
                                </button>
                              </>
                            )}
                            {aLeRole('ADMIN') && p.variantes.length > 1 && (
                              <button
                                type="button"
                                className="rounded p-1.5 text-muted-foreground hover:text-destructive"
                                aria-label={`Supprimer ${v.sku}`}
                                onClick={async () => {
                                  if (await confirm({ title: `Supprimer ${v.sku} ?`, message: 'Impossible si la variante a des mouvements (RG-01).', danger: true })) supprimerVariante.mutate(v)
                                }}
                              >
                                <Trash2 size={14} />
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            </div>

            <Card padding="none" className="mt-4">
              <h2 className="border-b border-border px-5 py-4 font-semibold">Stock par dépôt</h2>
              {lignesStock.length === 0 ? (
                <p className="px-5 py-6 text-sm text-muted-foreground">Aucun stock enregistré pour ce produit.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="px-4 py-2.5 font-medium">Variante</th>
                        <th className="px-4 py-2.5 font-medium">Dépôt</th>
                        <th className="px-4 py-2.5 font-medium">Empl.</th>
                        <th className="px-4 py-2.5 text-right font-medium">Physique</th>
                        <th className="px-4 py-2.5 text-right font-medium">Réservé</th>
                        <th className="px-4 py-2.5 text-right font-medium">Disponible</th>
                        {voitLesCouts && <th className="px-4 py-2.5 text-right font-medium">CUMP</th>}
                        {voitLesCouts && <th className="px-4 py-2.5 text-right font-medium">Valeur</th>}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {lignesStock.map((l) => (
                        <tr key={`${l.varianteId}-${l.depotId}`}>
                          <td className="px-4 py-2.5">{l.sku}</td>
                          <td className="px-4 py-2.5">{l.depotNom}</td>
                          <td className="px-4 py-2.5 text-muted-foreground">{l.emplacementCode ?? '—'}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{qte(l.qtePhysique)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums text-muted-foreground">{qte(l.qteReservee)}</td>
                          <td className="px-4 py-2.5 text-right font-semibold tabular-nums">{qte(l.qteDisponible)}</td>
                          {voitLesCouts && <td className="px-4 py-2.5 text-right tabular-nums">{mga(l.cump)}</td>}
                          {voitLesCouts && <td className="px-4 py-2.5 text-right tabular-nums">{mga(l.valeur)}</td>}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            <ProduitModal open={edition} onClose={() => setEdition(false)} produit={p} onSaved={maj} />
            <VarianteModal open={variante !== undefined} onClose={() => setVariante(undefined)} produit={p} variante={variante} />
          </>
        )}
      </QueryState>
    </Page>
  )
}

function Ligne({ label, valeur }: { label: string; valeur: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium">{valeur ?? '—'}</dd>
    </div>
  )
}
