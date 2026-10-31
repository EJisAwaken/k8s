'use client'

import { useFocusTrap } from '@/components/ui/use-focus-trap'

/**
 * Tiroir de navigation mobile (repris d'EcoleManagement) : voile, panneau et
 * comportement clavier — piège de tabulation, Échap, restitution du focus.
 * Le contenu (en-tête, menus) reste chez l'appelant.
 */
export function NavDrawer({
  open,
  onClose,
  children,
}: {
  open: boolean
  onClose: () => void
  children: React.ReactNode
}) {
  const panelRef = useFocusTrap<HTMLElement>({ open, onEscape: onClose, trigger: 'active' })

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div className="absolute inset-0 bg-black/40 animate-in fade-in" onClick={onClose} aria-hidden />
      <aside
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label="Menu de navigation"
        tabIndex={-1}
        className="absolute left-0 top-0 flex h-full w-64 flex-col border-r border-border bg-card shadow-xl outline-none duration-200 animate-in slide-in-from-left"
      >
        {children}
      </aside>
    </div>
  )
}
