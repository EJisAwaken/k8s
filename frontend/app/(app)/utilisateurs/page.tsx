'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Pencil, Plus, ShieldOff } from 'lucide-react'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/input'
import { Modal } from '@/components/ui/modal'
import { Badge } from '@/components/ui/badge'
import { QueryState } from '@/components/ui/query-state'
import { TempPasswordModal } from '@/components/ui/temp-password'
import { useConfirm } from '@/components/ui/confirm'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api/client'
import type { Utilisateur, UtilisateurCree } from '@/lib/api/types'
import { ROLE_LABELS, ROLES, type Role } from '@/lib/auth/types'

const DROITS: Record<Role, string> = {
  ADMIN: 'Tout, dont utilisateurs, paramètres et validation des inventaires.',
  ACHATS: 'Catalogue et approvisionnement ; ne fixe pas les prix de vente.',
  MAGASINIER: 'Mouvements, transferts, inventaires ; ne voit ni coûts ni marges.',
  COMMERCIAL: 'Clients et ventes (lot 2) ; ne modifie pas le stock directement.',
  COMPTABLE: 'Lecture seule : rapports, valorisation, exports, audit.',
}

export default function Utilisateurs() {
  const qc = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const [edition, setEdition] = useState<Utilisateur | null | undefined>(undefined)
  const [motDePasse, setMotDePasse] = useState<{ titre: string; valeur: string } | null>(null)
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<Utilisateur[]>('/users') })

  const reinit = useMutation({
    mutationFn: (u: Utilisateur) => api.post<UtilisateurCree>(`/users/${u.id}/reset-password`),
    onSuccess: (r) => { setMotDePasse({ titre: `Nouveau mot de passe — ${r.user.fullName}`, valeur: r.temporaryPassword }); void qc.invalidateQueries({ queryKey: ['users'] }) },
  })
  const reinit2fa = useMutation({
    mutationFn: (u: Utilisateur) => api.post(`/users/${u.id}/reset-2fa`),
    onSuccess: () => { toast.success('Double authentification désactivée.'); void qc.invalidateQueries({ queryKey: ['users'] }) },
  })

  return (
    <Page>
      <PageHeader title="Utilisateurs" subtitle="Comptes et rôles (cahier des charges §2)." actions={<Button onClick={() => setEdition(null)}><Plus size={16} /> Nouvel utilisateur</Button>} />
      <QueryState loading={users.isLoading} error={users.error} onRetry={() => void users.refetch()}>
        <Card padding="none">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Nom</th>
                  <th className="px-4 py-2.5 font-medium">Rôle</th>
                  <th className="px-4 py-2.5 font-medium">Sécurité</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {users.data?.map((u) => (
                  <tr key={u.id} className={u.active ? '' : 'opacity-60'}>
                    <td className="px-4 py-3">
                      <p className="font-medium">{u.fullName} {!u.active && <Badge variant="gray">Désactivé</Badge>}</p>
                      <p className="text-2xs text-muted-foreground">{u.email}{u.phone ? ` · ${u.phone}` : ''}</p>
                    </td>
                    <td className="px-4 py-3">{u.roleLabel}</td>
                    <td className="space-x-1 px-4 py-3">
                      {u.totpEnabled ? <Badge variant="green">2FA</Badge> : u.role === 'ADMIN' ? <Badge variant="red">2FA absente</Badge> : null}
                      {u.passwordChangeRequired && <Badge variant="yellow">Mot de passe temporaire</Badge>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <button type="button" className="rounded p-1.5 text-muted-foreground hover:text-foreground" onClick={() => setEdition(u)} aria-label={`Modifier ${u.fullName}`} title="Modifier"><Pencil size={14} /></button>
                      <button type="button" className="rounded p-1.5 text-muted-foreground hover:text-foreground" title="Réinitialiser le mot de passe" aria-label={`Réinitialiser le mot de passe de ${u.fullName}`}
                        onClick={async () => { if (await confirm({ title: 'Réinitialiser le mot de passe ?', message: `${u.fullName} recevra un mot de passe temporaire ; ses sessions ouvertes seront fermées.` })) reinit.mutate(u) }}>
                        <KeyRound size={14} />
                      </button>
                      {u.totpEnabled && (
                        <button type="button" className="rounded p-1.5 text-muted-foreground hover:text-destructive" title="Désactiver la 2FA (téléphone perdu)" aria-label={`Désactiver la 2FA de ${u.fullName}`}
                          onClick={async () => { if (await confirm({ title: 'Désactiver la double authentification ?', message: 'À faire seulement si la personne a perdu son téléphone.', danger: true })) reinit2fa.mutate(u) }}>
                          <ShieldOff size={14} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </QueryState>
      <UtilisateurModal utilisateur={edition} onClose={() => setEdition(undefined)} onCree={(r) => setMotDePasse({ titre: `Compte créé — ${r.user.fullName}`, valeur: r.temporaryPassword })} />
      <TempPasswordModal password={motDePasse?.valeur ?? null} title={motDePasse?.titre} onClose={() => setMotDePasse(null)} />
    </Page>
  )
}

function UtilisateurModal({ utilisateur, onClose, onCree }: {
  utilisateur: Utilisateur | null | undefined; onClose: () => void; onCree: (r: UtilisateurCree) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState({ email: '', fullName: '', phone: '', role: 'MAGASINIER' as Role, active: true })
  const [ouvert, setOuvert] = useState<Utilisateur | null | undefined>(undefined)
  if (utilisateur !== ouvert) {
    setOuvert(utilisateur)
    setF(utilisateur
      ? { email: utilisateur.email, fullName: utilisateur.fullName, phone: utilisateur.phone ?? '', role: utilisateur.role, active: utilisateur.active }
      : { email: '', fullName: '', phone: '', role: 'MAGASINIER', active: true })
  }
  const mutation = useMutation({
    mutationFn: (): Promise<Utilisateur | UtilisateurCree> =>
      utilisateur ? api.put<Utilisateur>(`/users/${utilisateur.id}`, f) : api.post<UtilisateurCree>('/users', f),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: ['users'] })
      if (utilisateur) toast.success('Utilisateur modifié.')
      else onCree(r as UtilisateurCree)
      onClose()
    },
  })
  return (
    <Modal open={utilisateur !== undefined} onClose={onClose} title={utilisateur ? `Modifier ${utilisateur.fullName}` : 'Nouvel utilisateur'}>
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
        <Input label="Nom complet" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} required />
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="E-mail (identifiant)" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} required />
          <Input label="Téléphone" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
        </div>
        <Select label="Rôle" value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })}>
          {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </Select>
        <p className="-mt-2 text-xs text-muted-foreground">{DROITS[f.role]}</p>
        {utilisateur && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} className="h-4 w-4 accent-[var(--primary)]" />
            Compte actif
          </label>
        )}
        {!utilisateur && <p className="text-xs text-muted-foreground">Un mot de passe temporaire sera affiché une seule fois après la création.</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={mutation.isPending}>{utilisateur ? 'Enregistrer' : 'Créer'}</Button>
        </div>
      </form>
    </Modal>
  )
}
