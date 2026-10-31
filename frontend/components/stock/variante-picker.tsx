'use client'

import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ScanBarcode, Search } from 'lucide-react'
import { clsx } from 'clsx'
import { api, ApiError } from '@/lib/api/client'
import type { VarianteResume } from '@/lib/api/types'
import { useDebouncedValue } from '@/components/ui/search-field'

/**
 * Choix d'un article : recherche par nom/SKU, ou scan à la douchette. Une
 * douchette USB/Bluetooth « tape » le code puis Entrée : Entrée déclenche donc
 * une recherche EXACTE par code-barres (ou SKU) et sélectionne l'article.
 */
export function VariantePicker({
  onSelect,
  placeholder = 'Rechercher un article ou scanner un code-barres…',
  autoFocus,
}: {
  onSelect: (v: VarianteResume) => void
  placeholder?: string
  autoFocus?: boolean
}) {
  const [q, setQ] = useState('')
  const [ouvert, setOuvert] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [index, setIndex] = useState(0)
  const debounced = useDebouncedValue(q, 250)
  const conteneur = useRef<HTMLDivElement>(null)

  const { data = [], isFetching } = useQuery({
    queryKey: ['variantes-recherche', debounced],
    queryFn: () => api.get<VarianteResume[]>('/catalogue/variantes/recherche', { q: debounced, limite: 12 }),
    enabled: debounced.trim().length >= 2,
  })

  useEffect(() => {
    function fermer(e: MouseEvent) {
      if (conteneur.current && !conteneur.current.contains(e.target as Node)) setOuvert(false)
    }
    document.addEventListener('mousedown', fermer)
    return () => document.removeEventListener('mousedown', fermer)
  }, [])

  function choisir(v: VarianteResume) {
    onSelect(v)
    setQ('')
    setOuvert(false)
    setErreur(null)
    setIndex(0)
  }

  async function scanner() {
    const code = q.trim()
    if (!code) return
    try {
      choisir(await api.get<VarianteResume>(`/catalogue/variantes/scan/${encodeURIComponent(code)}`))
    } catch (e) {
      // Pas un code exact : on laisse la liste de suggestions ouverte si elle existe.
      if (data.length > 0 && ouvert) choisir(data[Math.min(index, data.length - 1)])
      else setErreur(e instanceof ApiError ? e.detail : 'Article introuvable.')
    }
  }

  return (
    <div ref={conteneur} className="relative">
      <div className="relative">
        <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          value={q}
          autoFocus={autoFocus}
          placeholder={placeholder}
          onChange={(e) => { setQ(e.target.value); setOuvert(true); setErreur(null); setIndex(0) }}
          onFocus={() => setOuvert(true)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') { e.preventDefault(); void scanner() }
            if (e.key === 'ArrowDown') { e.preventDefault(); setIndex((i) => Math.min(i + 1, data.length - 1)) }
            if (e.key === 'ArrowUp') { e.preventDefault(); setIndex((i) => Math.max(i - 1, 0)) }
            if (e.key === 'Escape') setOuvert(false)
          }}
          className="h-10 w-full rounded-lg border border-input bg-background pl-9 pr-9 text-sm outline-none transition-colors placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30"
          aria-label="Rechercher un article"
        />
        <ScanBarcode size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
      </div>
      {erreur && <p className="mt-1 text-xs text-destructive">{erreur}</p>}
      {ouvert && debounced.trim().length >= 2 && (
        <ul className="absolute z-30 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-border bg-popover p-1 shadow-lg" role="listbox">
          {isFetching && data.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">Recherche…</li>}
          {!isFetching && data.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">Aucun article</li>}
          {data.map((v, i) => (
            <li key={v.id} role="option" aria-selected={i === index}>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choisir(v)}
                className={clsx('flex w-full items-center justify-between gap-3 rounded-md px-3 py-2 text-left text-sm', i === index ? 'bg-muted' : 'hover:bg-muted')}
              >
                <span className="min-w-0">
                  <span className="block truncate font-medium">{v.libelle}</span>
                  <span className="block text-2xs text-muted-foreground">{v.sku}{v.codeBarres ? ` · ${v.codeBarres}` : ''}</span>
                </span>
                {v.suiviSerie && <span className="shrink-0 rounded bg-primary/5 px-1.5 py-0.5 text-3xs font-semibold text-primary">N° SÉRIE</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
