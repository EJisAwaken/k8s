'use client'

// Composant repris d'EcoleManagement (design system commun).

import { X } from 'lucide-react'
import { useId } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { useT } from '@/providers/locale-provider'
import { useFocusTrap } from './use-focus-trap'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

export function Modal({ open, onClose, title, children, size = 'md' }: ModalProps) {
  const titleId = useId()
  const reduce = useReducedMotion()
  const t = useT()

  const dialogRef = useFocusTrap<HTMLDivElement>({
    open,
    onEscape: onClose,
    trigger: 'global',
    lockScroll: true,
  })

  const widths = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl' }

  // AnimatePresence gère l'entrée ET la sortie : le conteneur `fixed` n'est monté
  // que pendant l'ouverture (sinon il intercepterait les clics une fois fermé).
  // `useReducedMotion` : opacité seule, sans transform (accessibilité).
  return (
    <AnimatePresence>
      {open && (
        <motion.div key="modal" className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <motion.div
            className="absolute inset-0 bg-black/40"
            onClick={onClose}
            aria-hidden
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
          />
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            // Focalisable par le code seul (jamais par Tab) : c'est la cible de la
            // mise au point initiale et le refuge du piège de tabulation. Le liseré
            // est retiré, le dialogue étant déjà signalé par le voile et le panneau.
            tabIndex={-1}
            className={`relative flex max-h-[90vh] w-full flex-col ${widths[size]} rounded-xl bg-card shadow-xl outline-none`}
            initial={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.17, ease: 'easeOut' }}
          >
            <div className="flex items-center justify-between border-b border-border px-5 py-4">
              <h2 id={titleId} className="text-base font-semibold text-foreground">{title}</h2>
              <button
                // Sans `type`, un <button> vaut « submit » : la croix est hors du
                // <form> passé en `children`, mais l'omission est un piège à retardement.
                type="button"
                onClick={onClose}
                aria-label={t('ds.close')}
                // Cible tactile en PIXELS et non en rem : le mode compact réduit les
                // rem, or une taille de doigt ne se densifie pas.
                className="-mr-1 inline-flex items-center justify-center rounded-lg p-1 text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring pointer-coarse:min-h-[44px] pointer-coarse:min-w-[44px]"
              >
                <X size={18} aria-hidden />
              </button>
            </div>
            <div className="overflow-y-auto p-5">{children}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
