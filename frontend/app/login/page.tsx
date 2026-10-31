import type { Metadata } from 'next'
import { Suspense } from 'react'
import { LoginForm } from './login-form'

export const metadata: Metadata = { title: 'Connexion' }

export default function LoginPage() {
  return (
    <div className="grid min-h-dvh place-items-center bg-muted p-4">
      <Suspense>
        <LoginForm />
      </Suspense>
    </div>
  )
}
