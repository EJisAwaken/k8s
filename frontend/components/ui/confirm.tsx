'use client'

// Composant repris d'EcoleManagement (design system commun).

import { createContext, useCallback, useContext, useEffect, useId, useRef, useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { Button } from './button'
import { useT } from '@/providers/locale-provider'

interface ConfirmOptions {
  title: string
  /** Corps du message (texte ou nœud). */
  message?: React.ReactNode
  confirmLabel?: string
  cancelLabel?: string
  /** Style d'action dangereuse (bouton rouge). Par défaut true. */
  danger?: boolean
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>

const ConfirmContext = createContext<ConfirmFn>(async () => false)

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [options, setOptions] = useState<ConfirmOptions | null>(null)
  const [open, setOpen] = useState(false)
  const resolver = useRef<((ok: boolean) => void) | null>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  /** Élément qui avait le focus avant l'ouverture : on le lui rend à la fermeture. */
  const previouslyFocused = useRef<HTMLElement | null>(null)
  const reduce = useReducedMotion()
  const t = useT()
  const titleId = useId()
  const messageId = useId()

  const confirm = useCallback<ConfirmFn>((opts) => {
    previouslyFocused.current = document.activeElement as HTMLElement | null
    setOptions(opts)
    setOpen(true)
    return new Promise<boolean>((resolve) => { resolver.current = resolve })
  }, [])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      e.stopImmediatePropagation()
      e.preventDefault()
      resolver.current?.(false)
      resolver.current = null
      setOpen(false)
    }
    document.addEventListener('keydown', onKey, true)
    // Le focus va sur « Annuler » et non sur l'action : une confirmation est une
    // occasion de renoncer, la touche Entrée réflexe ne doit pas la franchir.
    const frame = requestAnimationFrame(() =>
      dialogRef.current?.querySelector<HTMLButtonElement>('[data-confirm-cancel]')?.focus())
    return () => {
      document.removeEventListener('keydown', onKey, true)
      cancelAnimationFrame(frame)
    }
  }, [open])

  // Résout tout de suite ; `options` reste affiché le temps de l'animation de
  // sortie (vidé à `onExitComplete`) pour ne pas casser le rendu pendant l'exit.
  const close = (ok: boolean) => {
    resolver.current?.(ok)
    resolver.current = null
    setOpen(false)
  }

  const danger = options?.danger ?? true

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <AnimatePresence onExitComplete={() => {
        setOptions(null)
        previouslyFocused.current?.focus?.()
        previouslyFocused.current = null
      }}>
        {open && options && (
          <motion.div key="confirm" className="fixed inset-0 z-[60] flex items-center justify-center p-4">
            <motion.div
              className="absolute inset-0 bg-black/40"
              onClick={() => close(false)}
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
            />
            <motion.div
              ref={dialogRef}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              aria-describedby={options.message ? messageId : undefined}
              className="relative w-full max-w-md rounded-xl bg-card shadow-xl"
              initial={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.17, ease: 'easeOut' }}
            >
              <div className="flex gap-4 p-5">
                <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${danger ? 'bg-destructive/5 text-destructive' : 'bg-primary/5 text-primary'}`}>
                  <AlertTriangle size={20} aria-hidden />
                </div>
                <div className="flex-1">
                  <h2 id={titleId} className="text-base font-semibold text-foreground">{options.title}</h2>
                  {options.message && (
                    <div id={messageId} className="mt-1 text-sm text-muted-foreground">{options.message}</div>
                  )}
                </div>
              </div>
              <div className="flex justify-end gap-2 border-t px-5 py-4">
                <Button variant="secondary" data-confirm-cancel onClick={() => close(false)}>
                  {options.cancelLabel ?? t('ds.cancel')}
                </Button>
                <Button variant={danger ? 'danger' : 'primary'} onClick={() => close(true)}>
                  {options.confirmLabel ?? t('ds.confirm')}
                </Button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </ConfirmContext.Provider>
  )
}

export const useConfirm = () => useContext(ConfirmContext)
