'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { Categorie, Depot, TypeMouvementInfo } from '@/lib/api/types'

/** Référentiels peu changeants : partagés par tous les écrans via le cache TanStack. */
export function useDepots() {
  return useQuery({ queryKey: ['depots'], queryFn: () => api.get<Depot[]>('/depots'), staleTime: 5 * 60_000 })
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<Categorie[]>('/catalogue/categories'),
    staleTime: 5 * 60_000,
  })
}

export function useTypesMouvement() {
  return useQuery({
    queryKey: ['types-mouvement'],
    queryFn: () => api.get<TypeMouvementInfo[]>('/stock/types-mouvement'),
    staleTime: Infinity,
  })
}
