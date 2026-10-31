'use client'

import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { Printer, Trash2 } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useToast } from '@/components/ui/toast'
import { VariantePicker } from '@/components/stock/variante-picker'
import { api, apiErrorMessage } from '@/lib/api/client'
import type { VarianteResume } from '@/lib/api/types'
import { mga } from '@/lib/format'

type Format = 'A4_3X8' | 'ROULEAU_50X30'
interface Ligne { variante: VarianteResume; copies: number }

export default function EtiquettesPage() {
  return (
    <Suspense>
      <Etiquettes />
    </Suspense>
  )
}

function Etiquettes() {
  const params = useSearchParams()
  const toast = useToast()
  const [lignes, setLignes] = useState<Ligne[]>([])
  const [format, setFormat] = useState<Format>('A4_3X8')
  const [prix, setPrix] = useState(true)
  const [envoi, setEnvoi] = useState(false)

  // Préremplissage depuis la fiche produit : /etiquettes?varianteIds=1,2,3
  useEffect(() => {
    const ids = params.get('varianteIds')
    if (!ids) return
    api.get<VarianteResume[]>('/catalogue/variantes', { ids })
      .then((vs) => setLignes(vs.map((v) => ({ variante: v, copies: 1 }))))
      .catch(() => {})
  }, [params])

  function ajouter(v: VarianteResume) {
    setLignes((ls) => ls.some((l) => l.variante.id === v.id)
      ? ls.map((l) => (l.variante.id === v.id ? { ...l, copies: l.copies + 1 } : l))
      : [...ls, { variante: v, copies: 1 }])
  }

  async function imprimer() {
    setEnvoi(true)
    try {
      await api.telecharger('/etiquettes', 'etiquettes.pdf', {
        ouvrir: true,
        body: { format, afficherPrix: prix, lignes: lignes.map((l) => ({ varianteId: l.variante.id, copies: l.copies })) },
      })
    } catch (e) {
      toast.error(apiErrorMessage(e))
    } finally {
      setEnvoi(false)
    }
  }

  const total = lignes.reduce((s, l) => s + l.copies, 0)
  return (
    <Page>
      <PageHeader title="Étiquettes" subtitle="Code-barres Code 128 lisible par la douchette, nom, SKU et prix détail." />
      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <VariantePicker onSelect={ajouter} autoFocus />
          {lignes.length === 0 ? (
            <p className="mt-4 rounded-lg border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
              Ajoutez des articles (recherche ou scan). Scanner deux fois un article ajoute une copie.
            </p>
          ) : (
            <ul className="mt-4 divide-y divide-border rounded-lg border border-border">
              {lignes.map((l, i) => (
                <li key={l.variante.id} className="flex items-center gap-3 p-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{l.variante.libelle}</p>
                    <p className="text-2xs text-muted-foreground">{l.variante.sku} · {l.variante.codeBarres} · {mga(l.variante.prixDetail)}</p>
                  </div>
                  <input
                    type="number" min={1} max={500} value={l.copies}
                    onChange={(e) => setLignes((ls) => ls.map((x, j) => (j === i ? { ...x, copies: Math.max(1, Number(e.target.value) || 1) } : x)))}
                    className="h-9 w-20 rounded-lg border border-input bg-background px-2 text-right text-sm"
                    aria-label={`Nombre d'étiquettes — ${l.variante.libelle}`}
                  />
                  <button type="button" onClick={() => setLignes((ls) => ls.filter((_, j) => j !== i))} className="rounded-md p-2 text-muted-foreground hover:text-destructive" aria-label={`Retirer ${l.variante.libelle}`}>
                    <Trash2 size={15} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="h-fit space-y-4">
          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-semibold">Format</legend>
            {([['A4_3X8', 'Planche A4 — 24 étiquettes', '70 × 37 mm, imprimante de bureau'], ['ROULEAU_50X30', 'Rouleau thermique', '50 × 30 mm, imprimante d\'étiquettes']] as const).map(([v, t, d]) => (
              <label key={v} className={`block cursor-pointer rounded-lg border p-3 text-sm ${format === v ? 'border-primary bg-primary/5' : 'border-border'}`}>
                <input type="radio" name="format" className="sr-only" checked={format === v} onChange={() => setFormat(v)} />
                <span className="font-medium">{t}</span>
                <span className="block text-xs text-muted-foreground">{d}</span>
              </label>
            ))}
          </fieldset>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={prix} onChange={(e) => setPrix(e.target.checked)} className="h-4 w-4 accent-[var(--primary)]" />
            Afficher le prix détail
          </label>
          <Button className="w-full" disabled={lignes.length === 0} loading={envoi} onClick={() => void imprimer()}>
            <Printer size={16} /> Générer {total > 0 ? `${total} étiquette${total > 1 ? 's' : ''}` : 'le PDF'}
          </Button>
        </Card>
      </div>
    </Page>
  )
}
