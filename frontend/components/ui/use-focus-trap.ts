'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useEffect, useRef } from 'react'

const FOCUSABLE =
  'button:not([disabled]), select:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'

const ABOVE = '[role="dialog"], [role="alertdialog"], [data-sonner-toaster]'

let lastTrigger: HTMLElement | null = null
if (typeof document !== 'undefined') {
  document.addEventListener(
    'focusin',
    e => {
      const el = e.target as HTMLElement | null
      if (el && el !== document.body && !el.closest?.(ABOVE)) lastTrigger = el
    },
    true,
  )
}

/** Commandes réellement tabulables du sous-arbre, dans l'ordre du document. */
function focusables(root: HTMLElement): HTMLElement[] {
  return (
    Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE))
      // `tabIndex >= 0` refiltre les `tabIndex={-1}` posés en JSX (le sélecteur
      // ne voit que l'attribut) ; la mesure écarte ce qui est masqué ou replié.
      .filter(el => el.tabIndex >= 0 && (el.offsetWidth > 0 || el.offsetHeight > 0))
  )
}

export interface FocusTrapOptions {
  /** La surcouche est-elle montée et visible ? */
  open: boolean
  onEscape?: () => void
  escapeMode?: 'bubble' | 'capture'
  trigger?: 'global' | 'active'
  lockScroll?: boolean
}

/**
 * Rend la ref à poser sur le **panneau** (le nœud qui porte `role="dialog"` et
 * `tabIndex={-1}`), pas sur le voile.
 */
export function useFocusTrap<T extends HTMLElement>({
  open,
  onEscape,
  escapeMode = 'bubble',
  trigger = 'global',
  lockScroll = false,
}: FocusTrapOptions) {
  const panelRef = useRef<T>(null)
  /** Élément qui avait le focus avant l'ouverture : on le lui rend à la fermeture. */
  const previouslyFocused = useRef<HTMLElement | null>(null)
  const escapeRef = useRef(onEscape)
  useEffect(() => {
    escapeRef.current = onEscape
  })

  // Échap, isolé de la gestion du focus : les deux n'ont ni la même phase
  // d'écoute ni les mêmes raisons de se rejouer.
  useEffect(() => {
    if (!open) return
    const capture = escapeMode === 'capture'
    const onKey = (e: KeyboardEvent) => {
      // Lue dans une constante locale : le compilateur ne garantit pas qu'une
      // propriété reste affinée après un appel de fonction.
      const close = escapeRef.current
      if (e.key !== 'Escape' || !close) return
      if (capture) {
        // `stopImmediatePropagation` et non `stopPropagation` : une modale
        // écoute elle aussi `keydown` sur `document`, et arrêter la propagation
        // ne sert à rien entre deux écouteurs du MÊME élément.
        e.stopImmediatePropagation()
        e.preventDefault()
      }
      close()
    }
    document.addEventListener('keydown', onKey, capture)
    return () => document.removeEventListener('keydown', onKey, capture)
  }, [open, escapeMode])

  // Gestion du focus, dépendante de `open` SEUL.
  useEffect(() => {
    if (!open) return
    const panel = panelRef.current

    if (trigger === 'global') {
      previouslyFocused.current = lastTrigger
    } else {
      const active = document.activeElement as HTMLElement | null
      previouslyFocused.current = active && active !== document.body ? active : null
    }

    const onTrapTab = (e: KeyboardEvent) => {
      if (e.key !== 'Tab' || !panel) return
      const items = focusables(panel)
      const active = document.activeElement
      if (items.length === 0) {
        // Surcouche sans aucune commande : on garde le focus sur le conteneur
        // plutôt que de le laisser filer dans la page masquée derrière.
        e.preventDefault()
        panel.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (e.shiftKey) {
        // Depuis le conteneur lui-même, Maj+Tab sortirait par le haut.
        if (active === first || active === panel) {
          e.preventDefault()
          last.focus()
        }
      } else if (active === last) {
        e.preventDefault()
        first.focus()
      }
    }
    panel?.addEventListener('keydown', onTrapTab)

    // Filet de sécurité pour les chemins que Tab ne couvre pas (retour depuis
    // la barre d'adresse, focus posé par du code). Une autre couche de dialogue
    // garde le droit de prendre le focus — voir ABOVE.
    const onFocusIn = (e: FocusEvent) => {
      const p = panelRef.current
      const target = e.target as HTMLElement | null
      if (!p || !target || p.contains(target) || target.closest?.(ABOVE)) return
      ;(focusables(p)[0] ?? p).focus()
    }
    document.addEventListener('focusin', onFocusIn)

    const previousOverflow = lockScroll ? document.body.style.overflow : null
    if (lockScroll) document.body.style.overflow = 'hidden'

    const frame = requestAnimationFrame(() => {
      const p = panelRef.current
      if (p && !p.contains(document.activeElement)) p.focus()
    })

    return () => {
      panel?.removeEventListener('keydown', onTrapTab)
      document.removeEventListener('focusin', onFocusIn)
      if (previousOverflow !== null) document.body.style.overflow = previousOverflow
      cancelAnimationFrame(frame)
      const active = document.activeElement as HTMLElement | null
      const back = previouslyFocused.current
      // `isConnected` : entre l'ouverture et la fermeture, la ligne qui portait
      // le bouton a pu être re-rendue ; focaliser un nœud détaché ne fait rien
      // et laisserait le focus sur <body>, donc au début de la page.
      if (back?.isConnected && (!active || active === document.body || panel?.contains(active))) {
        back.focus()
      }
    }
  }, [open, trigger, lockScroll])

  return panelRef
}
