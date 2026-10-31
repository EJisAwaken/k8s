'use client'

import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Download, FileUp, TriangleAlert } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { api, apiErrorMessage } from '@/lib/api/client'
import type { RapportImport } from '@/lib/api/types'

export default function Reprise() {
  return (
    <Page>
      <PageHeader
        title="Reprise de données"
        subtitle="Import Excel du catalogue, puis du stock initial valorisé. Tout ou rien : à la moindre erreur, rien n'est écrit."
      />
      <div className="grid gap-4 lg:grid-cols-2">
        <Etape
          numero={1}
          titre="Catalogue"
          description="Une ligne par variante. Les produits sont regroupés par référence ; une référence ou un SKU existant est mis à jour. Les catégories inconnues sont créées."
          modele="/reprise/modeles/catalogue"
          nomModele="modele-catalogue.xlsx"
          cible="/reprise/catalogue"
        />
        <Etape
          numero={2}
          titre="Stock initial"
          description="SKU, code dépôt, quantité et coût unitaire en ariary (obligatoire : le stock initial est valorisé). Numéros de série séparés par « ; » pour les articles suivis."
          modele="/reprise/modeles/stock-initial"
          nomModele="modele-stock-initial.xlsx"
          cible="/reprise/stock-initial"
        />
      </div>
    </Page>
  )
}

function Etape({ numero, titre, description, modele, nomModele, cible }: {
  numero: number; titre: string; description: string; modele: string; nomModele: string; cible: string
}) {
  const qc = useQueryClient()
  const [fichier, setFichier] = useState<File | null>(null)
  const [rapport, setRapport] = useState<RapportImport | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)
  const [envoi, setEnvoi] = useState(false)

  async function envoyer(simulation: boolean) {
    if (!fichier) return
    setEnvoi(true)
    setErreur(null)
    setRapport(null)
    const form = new FormData()
    form.append('file', fichier)
    try {
      const r = await api.post<RapportImport>(cible, form, { simulation })
      setRapport(r)
      if (r.applique) void qc.invalidateQueries()
    } catch (e) {
      setErreur(apiErrorMessage(e))
    } finally {
      setEnvoi(false)
    }
  }

  return (
    <Card className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">{numero}</span>
        <div>
          <h2 className="font-semibold">{titre}</h2>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
      </div>
      <Button variant="secondary" size="sm" onClick={() => void api.telecharger(modele, nomModele)}><Download size={14} /> Télécharger le modèle</Button>
      <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-dashed border-border px-4 py-5 text-sm hover:bg-muted/50">
        <FileUp size={18} className="text-muted-foreground" />
        <span className="flex-1 truncate">{fichier ? fichier.name : 'Choisir un fichier .xlsx…'}</span>
        <input type="file" accept=".xlsx" className="sr-only" onChange={(e) => { setFichier(e.target.files?.[0] ?? null); setRapport(null) }} />
      </label>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={!fichier} loading={envoi} onClick={() => void envoyer(true)}>Vérifier sans importer</Button>
        <Button disabled={!fichier} loading={envoi} onClick={() => void envoyer(false)}>Importer</Button>
      </div>
      {erreur && <p className="rounded-lg bg-destructive/5 px-3 py-2 text-sm text-destructive">{erreur}</p>}
      {rapport && (
        <div className={`rounded-lg px-4 py-3 text-sm ${rapport.erreurs.length ? 'bg-destructive/5' : 'bg-success/5'}`}>
          {rapport.erreurs.length === 0 ? (
            <p className="flex items-center gap-2 font-medium text-success">
              <CheckCircle2 size={16} />
              {rapport.simulation
                ? `Fichier valide : ${rapport.lignesLues} ligne(s), ${rapport.crees} création(s), ${rapport.misAJour} mise(s) à jour. Rien n'a été écrit.`
                : `Import terminé : ${rapport.crees} création(s), ${rapport.misAJour} mise(s) à jour${rapport.numeroDocument ? ` — document ${rapport.numeroDocument}` : ''}.`}
            </p>
          ) : (
            <>
              <p className="mb-2 flex items-center gap-2 font-medium text-destructive">
                <TriangleAlert size={16} /> {rapport.erreurs.length} erreur(s) : rien n&apos;a été importé.
              </p>
              <ul className="max-h-60 space-y-1 overflow-y-auto">
                {rapport.erreurs.map((e, i) => (
                  <li key={i}><span className="font-mono text-xs">Ligne {e.ligne}</span> — {e.message}</li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </Card>
  )
}
