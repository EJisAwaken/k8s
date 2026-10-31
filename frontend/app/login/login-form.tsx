'use client'

import { useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { Package2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { messages } from '@/lib/messages'

export function LoginForm() {
  const params = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [etape2fa, setEtape2fa] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [envoi, setEnvoi] = useState(false)

  async function soumettre(e: React.FormEvent) {
    e.preventDefault()
    setEnvoi(true)
    setErreur(null)
    try {
      const res = await fetch('/api/session/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, code: etape2fa ? code : undefined }),
      })
      const body = (await res.json().catch(() => ({}))) as { message?: string | null; code?: string | null }
      if (!res.ok) {
        if (body.code === 'TOTP_REQUIRED') {
          // Identifiants bons : on demande le second facteur.
          if (etape2fa) setErreur(body.message ?? 'Code invalide.')
          setEtape2fa(true)
        } else {
          setErreur(body.message ?? messages.technical)
        }
        setEnvoi(false)
        return
      }
      const next = params.get('next')
      // Seuls les chemins internes sont suivis (pas de redirection ouverte).
      window.location.assign(next && next.startsWith('/') && !next.startsWith('//') ? next : '/')
    } catch {
      setErreur(messages.network)
      setEnvoi(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="mb-6 flex flex-col items-center gap-3 text-center">
        <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
          <Package2 size={24} />
        </span>
        <div>
          <h1 className="text-xl font-bold tracking-tight">Nantenaina et tsanta joue ensemble</h1>
          <p className="text-sm text-muted-foreground">testeur , Esoalahy josefa, import Chine et suivi clients</p>
        </div>
      </div>
      <form onSubmit={soumettre} className="space-y-4 rounded-xl border border-border bg-card p-6 shadow-sm">
        {!etape2fa ? (
          <>
            <Input label="E-mail" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
            <Input label="Mot de passe" type="password" revealable autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              Saisissez le code à 6 chiffres de votre application d&apos;authentification, ou un code de secours.
            </p>
            <Input label="Code de vérification" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus />
          </>
        )}
        {erreur && (
          <p role="alert" className="rounded-lg bg-destructive/5 px-3 py-2 text-sm text-destructive">{erreur}</p>
        )}
        <Button type="submit" loading={envoi} className="w-full">
          {etape2fa ? 'Vérifier' : 'Se connecter'}
        </Button>
        {etape2fa && (
          <button type="button" className="w-full text-center text-xs text-muted-foreground hover:text-foreground" onClick={() => { setEtape2fa(false); setCode(''); setErreur(null) }}>
            Revenir à l&apos;identification
          </button>
        )}
      </form>
      <p className="mt-4 text-center text-xs text-muted-foreground">
        Mot de passe oublié ? Demandez une réinitialisation à votre administrateur.
      </p>
    </div>
  )
}
