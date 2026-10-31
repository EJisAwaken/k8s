import { redirect } from 'next/navigation'
import { AppShell } from '@/components/layout/app-shell'
import { readSession } from '@/lib/auth/session'
import { SessionProvider } from '@/providers/session-provider'

/** Espace connecté : la session est relue côté serveur (cookie httpOnly) à chaque requête. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const session = await readSession()
  if (!session) redirect('/login')

  return (
    <SessionProvider user={session}>
      <AppShell>{children}</AppShell>
    </SessionProvider>
  )
}
