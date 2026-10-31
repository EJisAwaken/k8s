'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'

/** Champ de recherche standard (icône loupe, filtrage au fil de la saisie). */
export function SearchField({ value, onChange, placeholder = 'Rechercher…', className = '', autoFocus }: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  autoFocus?: boolean
}) {
  return (
    <div className={`relative ${className}`}>
      <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
      <input aria-label={placeholder}
        type="search"
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        className="w-full rounded-lg border border-border bg-card py-2 pl-8 pr-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
      />
    </div>
  )
}

/** Valeur retardée (anti-rafale) pour les recherches côté serveur. */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(t)
  }, [value, delayMs])
  return debounced
}

/** Normalisation pour un filtrage insensible à la casse et aux accents. */
export function normalize(s: string): string {
  return s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
}

export function matches(haystack: string | null | undefined, needle: string): boolean {
  const words = normalize(needle).split(/\s+/).filter(Boolean)
  if (words.length === 0) return true
  const hay = normalize(haystack ?? '')
  return words.every(w => hay.includes(w))
}
