'use client'

import Link from 'next/link'
import { use, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCheck, Download, RotateCcw, ScanBarcode, Send, XCircle } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { QueryState } from '@/components/ui/query-state'
import { StatusChip } from '@/components/ui/status-chip'
import { useConfirm } from '@/components/ui/confirm'
import { useToast } from '@/components/ui/toast'
import { STATUT_INVENTAIRE } from '@/components/stock/statuts'
import { VariantePicker } from '@/components/stock/variante-picker'
import { api, ApiError } from '@/lib/api/client'
import type { Inventaire, InventaireLigne } from '@/lib/api/types'
import { horodatage, mga, qte } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

type Filtre = 'tous' | 'non-comptes' | 'ecarts'

export default function FicheInventaire({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const qc = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const { aLeRole, voitLesCouts } = useSession()
  const [filtre, setFiltre] = useState<Filtre>('tous')
  const [validation, setValidation] = useState(false)
  const [scan, setScan] = useState('')
  const [dernier, setDernier] = useState<InventaireLigne | null>(null)
  const champScan = useRef<HTMLInputElement>(null)

  const cle = ['inventaire', Number(id)]
  const inventaire = useQuery({ queryKey: cle, queryFn: () => api.get<Inventaire>(`/inventaires/${id}`) })
  const inv = inventaire.data
  const enCours = inv?.statut === 'EN_COURS'
  const peutCompter = enCours && aLeRole('ADMIN', 'MAGASINIER')

  const rafraichir = () => {
    void qc.invalidateQueries({ queryKey: cle })
    void qc.invalidateQueries({ queryKey: ['inventaires'] })
  }

  const compter = useMutation({
    mutationFn: (c: { varianteId: number; quantite: number; ajouter: boolean }) =>
      api.post<InventaireLigne>(`/inventaires/${id}/comptages`, c),
    onSuccess: (l) => { setDernier(l); rafraichir() },
  })
  const scanner = useMutation({
    mutationFn: (code: string) => api.post<InventaireLigne>(`/inventaires/${id}/scan/${encodeURIComponent(code)}`),
    onSuccess: (l) => { setDernier(l); setScan(''); rafraichir(); champScan.current?.focus() },
    onError: (e) => { toast.error(e instanceof ApiError ? e.detail : 'Code inconnu.'); setScan('') },
  })
  const action = useMutation({
    mutationFn: (chemin: string) => api.post<Inventaire>(`/inventaires/${id}/${chemin}`),
    onSuccess: (i) => { qc.setQueryData(cle, i); rafraichir() },
  })

  const lignes = useMemo(() => {
    const l = inv?.lignes ?? []
    if (filtre === 'non-comptes') return l.filter((x) => x.qteComptee == null)
    if (filtre === 'ecarts') return l.filter((x) => x.ecart != null && x.ecart !== 0)
    return l
  }, [inv, filtre])

  return (
    <Page>
      <Link href="/inventaires" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft size={14} /> Inventaires
      </Link>
      <QueryState loading={inventaire.isLoading} error={inventaire.error} onRetry={() => void inventaire.refetch()}>
        {inv && (
          <>
            <PageHeader
              title={`Inventaire ${inv.numero}`}
              subtitle={
                <span className="flex flex-wrap items-center gap-2">
                  {inv.depotNom} · {inv.type === 'COMPLET' ? 'complet' : 'tournant'}
                  <StatusChip status={inv.statut} styles={STATUT_INVENTAIRE} />
                </span>
              }
              actions={
                <>
                  <Button variant="secondary" onClick={() => void api.telecharger(`/inventaires/${id}/export`, `${inv.numero}.xlsx`)}>
                    <Download size={16} /> Rapport d&apos;écarts
                  </Button>
                  {peutCompter && (
                    <Button variant="secondary" loading={action.isPending} onClick={async () => {
                      if (await confirm({ title: 'Soumettre le comptage ?', message: 'Le comptage sera figé en attendant la validation de l\'administrateur.' })) action.mutate('soumission')
                    }}>
                      <Send size={16} /> Soumettre
                    </Button>
                  )}
                  {aLeRole('ADMIN') && inv.statut === 'A_VALIDER' && (
                    <Button variant="secondary" onClick={() => action.mutate('reouverture')}><RotateCcw size={16} /> Renvoyer au comptage</Button>
                  )}
                  {aLeRole('ADMIN') && (inv.statut === 'A_VALIDER' || inv.statut === 'EN_COURS') && (
                    <>
                      <Button variant="danger" onClick={async () => {
                        if (await confirm({ title: 'Annuler cet inventaire ?', message: 'Aucun écart ne sera appliqué.', danger: true })) action.mutate('annulation')
                      }}>
                        <XCircle size={16} /> Annuler
                      </Button>
                      <Button onClick={() => setValidation(true)}><CheckCheck size={16} /> Valider les écarts</Button>
                    </>
                  )}
                </>
              }
            />

            <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Indicateur label="Articles" valeur={qte(inv.nbLignes)} />
              <Indicateur label="Comptés" valeur={`${qte(inv.nbComptees)} / ${qte(inv.nbLignes)}`} />
              <Indicateur label="Écarts" valeur={qte(inv.nbEcarts)} />
              {voitLesCouts && <Indicateur label="Valeur des écarts" valeur={`${mga(inv.valeurEcartPositif)} / ${mga(inv.valeurEcartNegatif)}`} />}
            </div>

            {peutCompter && (
              <Card className="mb-4">
                <div className="grid gap-4 lg:grid-cols-2">
                  <form onSubmit={(e) => { e.preventDefault(); if (scan.trim()) scanner.mutate(scan.trim()) }}>
                    <label className="mb-1 flex items-center gap-2 text-sm font-medium"><ScanBarcode size={16} /> Scan : +1 par lecture</label>
                    <input
                      ref={champScan}
                      autoFocus
                      value={scan}
                      onChange={(e) => setScan(e.target.value)}
                      placeholder="Placez le curseur ici et scannez…"
                      className="h-11 w-full rounded-lg border border-input bg-background px-3 font-mono text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/30"
                      aria-label="Code-barres à scanner"
                    />
                  </form>
                  <div>
                    <p className="mb-1 text-sm font-medium">Sans code-barres : rechercher l&apos;article (+1)</p>
                    <VariantePicker onSelect={(v) => compter.mutate({ varianteId: v.id, quantite: 1, ajouter: true })} placeholder="Nom ou SKU…" />
                  </div>
                </div>
                {dernier && (
                  <p className="mt-3 rounded-lg bg-success/5 px-3 py-2 text-sm text-success">
                    {dernier.libelle} : <span className="font-semibold">{qte(dernier.qteComptee)}</span> compté(s)
                  </p>
                )}
              </Card>
            )}

            <Card padding="none">
              <div className="flex flex-wrap gap-1 border-b border-border p-2" role="tablist">
                {([['tous', 'Tous'], ['non-comptes', 'Non comptés'], ['ecarts', 'Écarts']] as const).map(([k, l]) => (
                  <button key={k} type="button" role="tab" aria-selected={filtre === k} onClick={() => setFiltre(k)}
                    className={`rounded-md px-3 py-1.5 text-sm font-medium ${filtre === k ? 'bg-primary/5 text-primary' : 'text-muted-foreground hover:bg-muted'}`}>
                    {l}
                  </button>
                ))}
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <tr>
                      <th className="px-4 py-2.5 font-medium">Article</th>
                      <th className="px-4 py-2.5 text-right font-medium">Théorique</th>
                      <th className="px-4 py-2.5 text-right font-medium">Compté</th>
                      <th className="px-4 py-2.5 text-right font-medium">Écart</th>
                      {voitLesCouts && <th className="px-4 py-2.5 text-right font-medium">Valeur écart</th>}
                      <th className="px-4 py-2.5 font-medium">Compté le</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {lignes.length === 0 && (
                      <tr><td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">Aucune ligne.</td></tr>
                    )}
                    {lignes.map((l) => (
                      <tr key={l.id}>
                        <td className="px-4 py-2.5">
                          <p className="font-medium">{l.libelle}</p>
                          <p className="text-2xs text-muted-foreground">{l.sku}{l.suiviSerie && ' · n° de série'}</p>
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums text-muted-foreground">{qte(l.qteTheorique)}</td>
                        <td className="px-4 py-2.5 text-right">
                          {peutCompter ? (
                            <input
                              type="number" min="0" step="any"
                              defaultValue={l.qteComptee ?? ''}
                              key={`${l.id}-${l.qteComptee}`}
                              onBlur={(e) => {
                                const v = e.target.value
                                if (v !== '' && Number(v) !== l.qteComptee) compter.mutate({ varianteId: l.varianteId, quantite: Number(v), ajouter: false })
                              }}
                              className="h-8 w-24 rounded-md border border-input bg-background px-2 text-right tabular-nums"
                              aria-label={`Quantité comptée — ${l.libelle}`}
                            />
                          ) : (
                            <span className="tabular-nums">{qte(l.qteComptee)}</span>
                          )}
                        </td>
                        <td className={`px-4 py-2.5 text-right font-semibold tabular-nums ${l.ecart == null || l.ecart === 0 ? 'text-muted-foreground' : l.ecart > 0 ? 'text-success' : 'text-destructive'}`}>
                          {l.ecart == null ? '—' : `${l.ecart > 0 ? '+' : ''}${qte(l.ecart)}`}
                        </td>
                        {voitLesCouts && <td className="px-4 py-2.5 text-right tabular-nums">{mga(l.valeurEcart)}</td>}
                        <td className="px-4 py-2.5 text-xs text-muted-foreground">{horodatage(l.compteAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
            <ValidationModal open={validation} onClose={() => setValidation(false)} inventaire={inv} onValide={(i) => { qc.setQueryData(cle, i); rafraichir(); toast.success('Inventaire validé : écarts appliqués au stock.') }} />
          </>
        )}
      </QueryState>
    </Page>
  )
}

function Indicateur({ label, valeur }: { label: string; valeur: string }) {
  return (
    <div className="rounded-xl border border-border bg-card px-4 py-3">
      <p className="text-xs uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{valeur}</p>
    </div>
  )
}

function ValidationModal({ open, onClose, inventaire, onValide }: {
  open: boolean; onClose: () => void; inventaire: Inventaire; onValide: (i: Inventaire) => void
}) {
  const [zero, setZero] = useState(inventaire.type === 'COMPLET')
  const nonComptes = inventaire.nbLignes - inventaire.nbComptees
  const mutation = useMutation({
    mutationFn: () => api.post<Inventaire>(`/inventaires/${inventaire.id}/validation`, undefined, { nonComptesAZero: zero }),
    onSuccess: (i) => { onValide(i); onClose() },
  })
  return (
    <Modal open={open} onClose={onClose} title="Valider les écarts">
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Chaque écart devient un mouvement « écart d&apos;inventaire » sous le numéro {inventaire.numero}.
          Un mouvement validé ne se modifie plus : il se corrige par annulation.
        </p>
        {nonComptes > 0 && (
          <label className="flex items-start gap-3 rounded-lg border border-border p-3 text-sm">
            <input type="checkbox" className="mt-0.5 h-4 w-4 accent-[var(--primary)]" checked={zero} onChange={(e) => setZero(e.target.checked)} />
            <span>
              <span className="font-medium">Considérer les {nonComptes} article(s) non compté(s) comme absents (0)</span>
              <span className="block text-xs text-muted-foreground">Sinon ils sont ignorés et leur stock ne change pas.</span>
            </span>
          </label>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Annuler</Button>
          <Button loading={mutation.isPending} onClick={() => mutation.mutate()}>Valider et appliquer</Button>
        </div>
      </div>
    </Modal>
  )
}
