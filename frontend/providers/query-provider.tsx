'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { ApiError, apiErrorMessage } from '@/lib/api/client'

/** Une mutation en échec affiche le message du backend (rédigé pour l'utilisateur). */
function reportMutationError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) return
  toast.error(apiErrorMessage(error))
}

export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 1,
            staleTime: 30_000,
          },
          mutations: { onError: reportMutationError },
        },
      }),
  )
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
