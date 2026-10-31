'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useState } from 'react'
import { Copy, Check } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { useT } from '@/providers/locale-provider'

export function TempPasswordModal({ password, onClose, title, intro, note }: {
  password: string | null; onClose: () => void; title?: string; intro?: string; note?: string
}) {
  const t = useT()
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    if (!password) return
    try {
      await navigator.clipboard.writeText(password)
      setCopied(true)
      // Réinitialisation dans un gestionnaire d'événement, jamais dans un effet :
      // pas de setState au rendu, pas de boucle possible.
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Presse-papiers indisponible (http, permission refusée) : le mot de passe
      // reste affiché et sélectionnable, rien n'est perdu.
    }
  }

  return (
    <Modal open={!!password} onClose={onClose} title={title ?? t('tmp.title')}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground text-pretty">{intro ?? t('tmp.intro')}</p>

        <div className="flex items-center justify-between gap-3 rounded-lg bg-muted px-4 py-3">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('tmp.label')}</p>
            <p className="mt-0.5 select-all break-all font-mono text-lg tabular-nums text-foreground">{password}</p>
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={copy} className="shrink-0">
            {copied ? <Check size={14} /> : <Copy size={14} />}
            {copied ? t('tmp.copied') : t('tmp.copy')}
          </Button>
        </div>

        <p className="text-xs text-muted-foreground text-pretty">{note ?? t('tmp.note')}</p>

        <div className="flex justify-end">
          <Button type="button" onClick={onClose}>{t('act.close')}</Button>
        </div>
      </div>
    </Modal>
  )
}
