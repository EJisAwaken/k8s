'use client'

import Link from 'next/link'
import { useState } from 'react'
import { ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { api, apiErrorMessage } from '@/lib/api/client'
import { useSession } from '@/providers/session-provider'

/**
 * Deux exigences de sécurité du cahier (§6) portées à l'écran :
 * - mot de passe temporaire ou expiré → modale BLOQUANTE de changement ;
 * - administrateur sans 2FA → bandeau permanent qui renvoie vers « Mon compte ».
 */
export function AccountGuard() {
  const { user } = useSession()
  if (user.passwordExpired) return <ChangementObligatoire />
  if (user.twoFactorSetupRequired) {
    return (
      <div className="fixed inset-x-0 bottom-0 z-40 flex items-center justify-center gap-3 bg-warning px-4 py-2.5 text-xs font-semibold text-warning-foreground print:hidden md:left-60">
        <ShieldAlert size={16} />
        <span>La double authentification est obligatoire pour un administrateur.</span>
        <Link href="/profil" className="rounded bg-warning-foreground/15 px-2 py-1 uppercase tracking-wider hover:bg-warning-foreground/25">
          L&apos;activer
        </Link>
      </div>
    )
  }
  return null
}

function ChangementObligatoire() {
  const [actuel, setActuel] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [envoi, setEnvoi] = useState(false)

  async function valider(e: React.FormEvent) {
    e.preventDefault()
    if (nouveau !== confirmation) {
      setErreur('Les deux saisies du nouveau mot de passe ne correspondent pas.')
      return
    }
    setEnvoi(true)
    setErreur(null)
    try {
      await api.post('/auth/change-password', { currentPassword: actuel, newPassword: nouveau })
      // Le refresh relit le compte : le drapeau « mot de passe temporaire » tombe.
      await fetch('/api/session/refresh', { method: 'POST' })
      window.location.reload()
    } catch (err) {
      setErreur(apiErrorMessage(err))
      setEnvoi(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4" role="dialog" aria-modal="true" aria-labelledby="pwd-titre">
      <form onSubmit={valider} className="w-full max-w-md space-y-4 rounded-xl border border-border bg-card p-6 shadow-xl">
        <div>
          <h2 id="pwd-titre" className="text-lg font-semibold">Définissez votre mot de passe</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Votre mot de passe actuel est temporaire. Choisissez-en un personnel pour continuer :
            8 caractères au moins, avec une majuscule, une minuscule et un chiffre.
          </p>
        </div>
        <Input label="Mot de passe actuel (temporaire)" type="password" revealable value={actuel} onChange={(e) => setActuel(e.target.value)} required autoComplete="current-password" />
        <Input label="Nouveau mot de passe" type="password" revealable value={nouveau} onChange={(e) => setNouveau(e.target.value)} required autoComplete="new-password" />
        <Input label="Confirmation" type="password" revealable value={confirmation} onChange={(e) => setConfirmation(e.target.value)} required autoComplete="new-password" />
        {erreur && <p className="rounded-lg bg-destructive/5 px-3 py-2 text-sm text-destructive">{erreur}</p>}
        <Button type="submit" loading={envoi} className="w-full">Enregistrer</Button>
      </form>
    </div>
  )
}
