'use client'

import { Trash2 } from 'lucide-react'
import type { VarianteResume } from '@/lib/api/types'
import { VariantePicker } from '@/components/stock/variante-picker'

export interface LigneSaisie {
  variante: VarianteResume
  quantite: string
  coutUnitaire: string
  numerosSerie: string
}

/**
 * Lignes d'un mouvement : un article par ligne (scanner deux fois le même
 * article ajoute 1 à sa quantité). Pour un article suivi par numéro de série,
 * un numéro par ligne de la zone de texte — la quantité en découle.
 */
export function LignesEditor({
  lignes,
  onChange,
  avecCout,
}: {
  lignes: LigneSaisie[]
  onChange: (lignes: LigneSaisie[]) => void
  avecCout: boolean
}) {
  function ajouter(v: VarianteResume) {
    const existante = lignes.findIndex((l) => l.variante.id === v.id)
    if (existante >= 0 && !v.suiviSerie) {
      const copie = [...lignes]
      copie[existante] = { ...copie[existante], quantite: String((Number(copie[existante].quantite) || 0) + 1) }
      onChange(copie)
      return
    }
    if (existante >= 0) return
    onChange([...lignes, { variante: v, quantite: v.suiviSerie ? '0' : '1', coutUnitaire: '', numerosSerie: '' }])
  }

  function maj(i: number, patch: Partial<LigneSaisie>) {
    const copie = [...lignes]
    copie[i] = { ...copie[i], ...patch }
    if (patch.numerosSerie !== undefined && copie[i].variante.suiviSerie) {
      copie[i].quantite = String(numeros(copie[i].numerosSerie).length)
    }
    onChange(copie)
  }

  return (
    <div className="space-y-3">
      <VariantePicker onSelect={ajouter} />
      {lignes.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
          Recherchez ou scannez un article pour l&apos;ajouter.
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {lignes.map((l, i) => (
            <li key={l.variante.id} className="space-y-2 p-3">
              <div className="flex items-start gap-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{l.variante.libelle}</p>
                  <p className="text-2xs text-muted-foreground">{l.variante.sku} · {l.variante.unite}</p>
                </div>
                <label className="w-24">
                  <span className="sr-only">Quantité</span>
                  <input
                    type="number" min="0" step="any" inputMode="decimal"
                    value={l.quantite}
                    disabled={l.variante.suiviSerie}
                    onChange={(e) => maj(i, { quantite: e.target.value })}
                    className="h-9 w-full rounded-lg border border-input bg-background px-2 text-right text-sm tabular-nums disabled:opacity-60"
                    aria-label={`Quantité — ${l.variante.libelle}`}
                  />
                </label>
                {avecCout && (
                  <label className="w-32">
                    <span className="sr-only">Coût unitaire</span>
                    <input
                      type="number" min="0" step="1" inputMode="numeric"
                      placeholder="Coût (Ar)"
                      value={l.coutUnitaire}
                      onChange={(e) => maj(i, { coutUnitaire: e.target.value })}
                      className="h-9 w-full rounded-lg border border-input bg-background px-2 text-right text-sm tabular-nums"
                      aria-label={`Coût unitaire en ariary — ${l.variante.libelle}`}
                    />
                  </label>
                )}
                <button type="button" onClick={() => onChange(lignes.filter((_, j) => j !== i))} className="rounded-md p-2 text-muted-foreground hover:bg-destructive/5 hover:text-destructive" aria-label={`Retirer ${l.variante.libelle}`}>
                  <Trash2 size={15} />
                </button>
              </div>
              {l.variante.suiviSerie && (
                <textarea
                  rows={2}
                  value={l.numerosSerie}
                  onChange={(e) => maj(i, { numerosSerie: e.target.value })}
                  placeholder="Numéros de série / IMEI — un par ligne (scannez-les à la suite)"
                  className="w-full rounded-lg border border-input bg-background px-3 py-2 font-mono text-xs"
                  aria-label={`Numéros de série — ${l.variante.libelle}`}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function numeros(texte: string): string[] {
  return texte.split(/[\n,;]+/).map((s) => s.trim()).filter(Boolean)
}

/** Lignes prêtes pour l'API ; `null` si une quantité est invalide. */
export function versApi(lignes: LigneSaisie[], avecCout: boolean) {
  const sortie = lignes.map((l) => ({
    varianteId: l.variante.id,
    quantite: Number(l.quantite),
    coutUnitaire: avecCout && l.coutUnitaire !== '' ? Number(l.coutUnitaire) : null,
    numerosSerie: l.variante.suiviSerie ? numeros(l.numerosSerie) : undefined,
  }))
  return sortie.every((l) => Number.isFinite(l.quantite) && l.quantite > 0) ? sortie : null
}
