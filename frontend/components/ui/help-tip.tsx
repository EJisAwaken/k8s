'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useEffect, useRef, useState } from 'react'
import { clsx } from 'clsx'
import { HelpCircle } from 'lucide-react'
import { useT } from '@/providers/locale-provider'

/**
 * Bulle d'aide contextuelle (UX-4) : un « ? » discret qui ouvre une infobulle
 * explicative au clic, sans quitter la page. Fermeture : clic extérieur ou Échap.
 */
export function HelpTip({ text, className }: { text: string; className?: string }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)
  const t = useT()

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDown)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <span ref={ref} className={clsx('relative inline-flex', className)}>
      <button
        type="button"
        aria-label={t('ds.help')}
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
        className="inline-flex items-center justify-center rounded-full text-muted-foreground transition-colors hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring pointer-coarse:min-h-[44px] pointer-coarse:min-w-[44px]"
      >
        <HelpCircle size={15} />
      </button>
      {open && (
        <span
          role="tooltip"
          className="absolute left-0 top-full z-50 mt-2 block w-72 rounded-lg border border-border bg-card p-3 text-xs font-normal leading-relaxed text-foreground shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  )
}
