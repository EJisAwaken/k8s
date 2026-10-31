'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Laptop, ShieldCheck, ShieldOff } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { SessionAppareil, Utilisateur } from '@/lib/api/types'
import { ROLE_LABELS } from '@/lib/auth/types'
import { horodatage } from '@/lib/format'
import { useSession } from '@/providers/session-provider'

interface Setup2fa { secret: string; uri: string; qrPng: string; recoveryCodes: string[] }

export default function Profil() {
  const { user } = useSession()
  const moi = useQuery({ queryKey: ['moi'], queryFn: () => api.get<Utilisateur>('/auth/me') })
  return (
    <Page>
      <PageHeader title="Mon compte" subtitle={`${user.fullName} · ${user.email} · ${ROLE_LABELS[user.role]}`} />
      <div className="grid gap-4 lg:grid-cols-2">
        <MotDePasse />
        {moi.data && <DoubleAuthentification actif={moi.data.totpEnabled} />}
        <Sessions />
      </div>
    </Page>
  )
}

function MotDePasse() {
  const toast = useToast()
  const [f, setF] = useState({ actuel: '', nouveau: '', confirmation: '' })
  const mutation = useMutation({
    mutationFn: () => api.post('/auth/change-password', { currentPassword: f.actuel, newPassword: f.nouveau }),
    onSuccess: () => { toast.success('Mot de passe changé. Vos autres appareils ont été déconnectés.'); setF({ actuel: '', nouveau: '', confirmation: '' }) },
  })
  const different = f.confirmation !== '' && f.nouveau !== f.confirmation
  return (
    <Card>
      <h2 className="mb-4 font-semibold">Mot de passe</h2>
      <form onSubmit={(e) => { e.preventDefault(); if (!different) mutation.mutate() }} className="space-y-3">
        <Input label="Mot de passe actuel" type="password" value={f.actuel} onChange={(e) => setF({ ...f, actuel: e.target.value })} required autoComplete="current-password" />
        <Input label="Nouveau mot de passe" type="password" value={f.nouveau} onChange={(e) => setF({ ...f, nouveau: e.target.value })} required autoComplete="new-password" />
        <Input label="Confirmation" type="password" value={f.confirmation} onChange={(e) => setF({ ...f, confirmation: e.target.value })} required autoComplete="new-password"
          error={different ? 'Les deux saisies ne correspondent pas.' : undefined} />
        <Button type="submit" loading={mutation.isPending}>Changer le mot de passe</Button>
      </form>
    </Card>
  )
}

function DoubleAuthentification({ actif }: { actif: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [setup, setSetup] = useState<Setup2fa | null>(null)
  const [code, setCode] = useState('')
  const demarrer = useMutation({ mutationFn: () => api.post<Setup2fa>('/auth/2fa/setup'), onSuccess: setSetup })
  const activer = useMutation({
    mutationFn: () => api.post('/auth/2fa/enable', undefined, { code }),
    onSuccess: async () => {
      toast.success('Double authentification activée.')
      setSetup(null)
      setCode('')
      // Rafraîchit la session : le bandeau « 2FA obligatoire » disparaît.
      await fetch('/api/session/refresh', { method: 'POST' })
      window.location.reload()
    },
  })
  const desactiver = useMutation({
    mutationFn: () => api.post('/auth/2fa/disable', undefined, { code }),
    onSuccess: () => { toast.success('Double authentification désactivée.'); setCode(''); void qc.invalidateQueries({ queryKey: ['moi'] }) },
  })

  return (
    <Card>
      <h2 className="mb-1 flex items-center gap-2 font-semibold">
        Double authentification {actif ? <Badge variant="green">Active</Badge> : <Badge variant="gray">Inactive</Badge>}
      </h2>
      <p className="mb-4 text-sm text-muted-foreground">
        Un code à 6 chiffres de votre téléphone (Google Authenticator, Aegis, 2FAS…) est demandé à chaque connexion.
      </p>
      {actif ? (
        <form onSubmit={(e) => { e.preventDefault(); desactiver.mutate() }} className="flex items-end gap-2">
          <div className="flex-1"><Input label="Code actuel" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} required /></div>
          <Button type="submit" variant="danger" loading={desactiver.isPending}><ShieldOff size={14} /> Désactiver</Button>
        </form>
      ) : !setup ? (
        <Button onClick={() => demarrer.mutate()} loading={demarrer.isPending}><ShieldCheck size={16} /> Activer</Button>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-col gap-4 sm:flex-row">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={setup.qrPng} alt="QR code à scanner avec l'application d'authentification" className="h-40 w-40 rounded-lg border border-border bg-white p-2" />
            <div className="text-sm">
              <p>1. Scannez ce QR code avec votre application.</p>
              <p className="mt-1 text-muted-foreground">Ou saisissez la clé : <span className="break-all font-mono text-foreground">{setup.secret}</span></p>
              <p className="mt-3">2. Notez ces codes de secours (un usage chacun) :</p>
              <p className="mt-1 grid grid-cols-2 gap-x-4 font-mono text-xs">{setup.recoveryCodes.map((c) => <span key={c}>{c}</span>)}</p>
            </div>
          </div>
          <form onSubmit={(e) => { e.preventDefault(); activer.mutate() }} className="flex items-end gap-2">
            <div className="flex-1"><Input label="3. Code affiché par l'application" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus /></div>
            <Button type="submit" loading={activer.isPending}>Confirmer</Button>
          </form>
        </div>
      )}
    </Card>
  )
}

function Sessions() {
  const qc = useQueryClient()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<SessionAppareil[]>('/auth/sessions') })
  const revoquer = useMutation({
    mutationFn: (id: number) => api.delete(`/auth/sessions/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['sessions'] }),
  })
  return (
    <Card className="lg:col-span-2">
      <h2 className="mb-4 font-semibold">Appareils connectés</h2>
      <ul className="divide-y divide-border">
        {sessions.data?.map((s) => (
          <li key={s.id} className="flex items-center gap-3 py-3 text-sm">
            <Laptop size={18} className="shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="truncate">{s.userAgent ?? 'Appareil inconnu'} {s.current && <Badge variant="green">Cet appareil</Badge>}</p>
              <p className="text-2xs text-muted-foreground">{s.ipAddress} · dernière activité {horodatage(s.lastUsedAt)}</p>
            </div>
            {!s.current && <Button size="sm" variant="ghost" onClick={() => revoquer.mutate(s.id)}>Déconnecter</Button>}
          </li>
        ))}
      </ul>
    </Card>
  )
}
