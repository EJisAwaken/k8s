'use client'

import { createContext, useCallback, useContext } from 'react'
import type { Role, SessionUser } from '@/lib/auth/types'

interface SessionState {
  user: SessionUser
  /** Le profil voit-il les coûts d'achat et les marges ? (cahier §2 : pas le magasinier) */
  voitLesCouts: boolean
  aLeRole: (...roles: Role[]) => boolean
  logout: () => Promise<void>
}

const SessionContext = createContext<SessionState | null>(null)

/**
 * Session de l'utilisateur, lue côté serveur dans le layout (cookie httpOnly)
 * puis transmise ici. Elle sert à l'AFFICHAGE (menus, boutons) : le backend
 * reste seul juge des droits.
 */
export function SessionProvider({ user, children }: { user: SessionUser; children: React.ReactNode }) {
  const aLeRole = useCallback((...roles: Role[]) => roles.includes(user.role), [user.role])
  const logout = useCallback(async () => {
    await fetch('/api/session/logout', { method: 'POST' }).catch(() => {})
    // Rechargement complet voulu : il purge tout l'état client de la session.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign('/login')
  }, [])
  return (
    <SessionContext.Provider value={{ user, voitLesCouts: user.role !== 'MAGASINIER', aLeRole, logout }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession(): SessionState {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession doit être utilisé sous <SessionProvider>')
  return ctx
}
