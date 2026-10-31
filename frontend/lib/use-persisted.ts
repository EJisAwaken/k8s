'use client'

import { useCallback, useMemo, useSyncExternalStore } from 'react'

const EVENEMENT = 'stock:persisted'

function lire(cle: string): string | null {
  try {
    return localStorage.getItem(cle)
  } catch {
    return null // navigation privée, stockage refusé
  }
}

function abonner(rappel: () => void) {
  window.addEventListener('storage', rappel)
  window.addEventListener(EVENEMENT, rappel)
  return () => {
    window.removeEventListener('storage', rappel)
    window.removeEventListener(EVENEMENT, rappel)
  }
}

/**
 * `useState` mémorisé dans le navigateur (colonnes masquées, filtres) —
 * confort par poste uniquement, jamais une donnée métier. Lu via
 * `useSyncExternalStore` : rendu serveur = valeur initiale, puis la valeur
 * stockée au montage, sans erreur d'hydratation.
 */
export function usePersistedState<T>(key: string, initial: T) {
  const cle = `stock.${key}`
  const brut = useSyncExternalStore(abonner, () => lire(cle), () => null)
  const value = useMemo<T>(() => {
    if (brut == null) return initial
    try {
      return JSON.parse(brut) as T
    } catch {
      return initial
    }
  }, [brut, initial])

  const setValue = useCallback(
    (suivant: T | ((precedent: T) => T)) => {
      const v = typeof suivant === 'function' ? (suivant as (p: T) => T)(value) : suivant
      try {
        localStorage.setItem(cle, JSON.stringify(v))
      } catch { /* stockage plein ou refusé */ }
      window.dispatchEvent(new Event(EVENEMENT))
    },
    [cle, value],
  )

  return [value, setValue] as const
}
