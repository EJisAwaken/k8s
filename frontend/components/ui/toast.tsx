'use client'

// Composant repris d'EcoleManagement (design system commun).

import { Toaster, toast } from 'sonner'
import { useTheme } from 'next-themes'
import { useT } from '@/providers/locale-provider'

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const { resolvedTheme } = useTheme()
  return (
    <>
      {children}
      <Toaster
        position="bottom-right"
        theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
        richColors
        closeButton
      />
    </>
  )
}

export const useToast = () => {
  const t = useT()
  return {
    success: (message: string) => toast.success(message),
    error: (message: string) => toast.error(message),
    warning: (message: string, description?: string) =>
      toast.warning(message, description ? { description, duration: 8000 } : undefined),
    undoable: (message: string, onUndo: () => void) =>
      toast.success(message, { action: { label: t('ds.cancel'), onClick: onUndo }, duration: 10_000 }),
  }
}
