'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useMemo, useState } from 'react'
import { clsx } from 'clsx'
import { LogOut, Menu, Package2, X } from 'lucide-react'
import { NavDrawer } from '@/components/layout/nav-drawer'
import { PageTransition } from '@/components/layout/page-transition'
import { AccountGuard } from '@/components/layout/account-guard'
import { ThemeToggle } from '@/components/layout/theme-toggle'
import { ToastProvider } from '@/components/ui/toast'
import { ConfirmProvider } from '@/components/ui/confirm'
import { useSession } from '@/providers/session-provider'
import { ROLE_LABELS } from '@/lib/auth/types'
import { MENUS, menusPour } from '@/lib/navigation'

/** Entrée active = celle dont le chemin est le plus long préfixe de l'URL. */
function hrefActif(pathname: string): string | null {
  let meilleur: string | null = null
  for (const g of MENUS) {
    for (const i of g.items) {
      const ok = i.href === '/' ? pathname === '/' : pathname === i.href || pathname.startsWith(`${i.href}/`)
      if (ok && (meilleur === null || i.href.length > meilleur.length)) meilleur = i.href
    }
  }
  return meilleur
}

function Brand() {
  return (
    <Link href="/" className="flex min-w-0 items-center gap-2">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Package2 size={18} />
      </span>
      <span className="truncate text-lg font-bold text-primary">Stock Import</span>
    </Link>
  )
}

function Nav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname()
  const { user } = useSession()
  const groupes = useMemo(() => menusPour(user.role), [user.role])
  const actif = hrefActif(pathname)

  return (
    <nav className="flex-1 overflow-y-auto px-3 py-3" aria-label="Navigation principale">
      {groupes.map((g) => (
        <div key={g.label || 'racine'} className="mb-2">
          {g.label && (
            <p className="mb-1 mt-3 px-3 text-3xs font-semibold uppercase tracking-widest text-muted-foreground">
              {g.label}
            </p>
          )}
          <ul className="space-y-0.5">
            {g.items.map(({ href, label, icon: Icon }) => {
              const active = href === actif
              return (
                <li key={href}>
                  <Link
                    href={href}
                    onClick={onNavigate}
                    aria-current={active ? 'page' : undefined}
                    className={clsx(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring',
                      active ? 'bg-primary/5 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                    )}
                  >
                    <Icon size={16} />
                    <span className="flex-1">{label}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </nav>
  )
}

function Logout() {
  const { logout } = useSession()
  return (
    <div className="border-t border-border p-3">
      <button
        type="button"
        onClick={() => void logout()}
        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-destructive/5 hover:text-destructive"
      >
        <LogOut size={16} />
        Se déconnecter
      </button>
    </div>
  )
}

function Topbar({ onMenu }: { onMenu: () => void }) {
  const { user } = useSession()
  const initiales = user.fullName
    .split(/\s+/)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-border bg-card px-4 print:hidden md:px-6">
      <button
        type="button"
        onClick={onMenu}
        aria-label="Ouvrir le menu"
        className="rounded-md p-1.5 text-muted-foreground transition-colors hover:text-foreground md:hidden"
      >
        <Menu size={20} />
      </button>
      <div className="flex-1" />
      <ThemeToggle />
      <Link href="/profil" className="flex items-center gap-3 rounded-lg px-2 py-1 transition-colors hover:bg-muted">
        <span className="hidden text-right sm:block">
          <span className="block text-sm font-medium leading-tight">{user.fullName}</span>
          <span className="block text-2xs text-muted-foreground">{ROLE_LABELS[user.role]}</span>
        </span>
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
          {initiales}
        </span>
      </Link>
    </header>
  )
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <ToastProvider>
      <ConfirmProvider>
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[100] focus:rounded-lg focus:bg-primary focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-primary-foreground"
        >
          Aller au contenu
        </a>
        <div className="flex h-dvh overflow-hidden print:block print:h-auto print:overflow-visible">
          <aside className="hidden h-full w-60 flex-col border-r border-border bg-card md:flex">
            <div className="flex h-16 items-center border-b border-border px-5">
              <Brand />
            </div>
            <Nav />
            <Logout />
          </aside>

          <NavDrawer open={mobileOpen} onClose={() => setMobileOpen(false)}>
            <div className="flex h-16 items-center justify-between border-b border-border px-5">
              <Brand />
              <button
                type="button"
                onClick={() => setMobileOpen(false)}
                aria-label="Fermer le menu"
                className="text-muted-foreground transition-colors hover:text-foreground"
              >
                <X size={18} />
              </button>
            </div>
            <Nav onNavigate={() => setMobileOpen(false)} />
            <Logout />
          </NavDrawer>

          <div className="flex flex-1 flex-col overflow-hidden print:block print:overflow-visible">
            <Topbar onMenu={() => setMobileOpen(true)} />
            <main id="main-content" tabIndex={-1} className="flex-1 overflow-y-auto outline-none print:overflow-visible">
              <PageTransition>{children}</PageTransition>
            </main>
          </div>
        </div>
        <AccountGuard />
      </ConfirmProvider>
    </ToastProvider>
  )
}
