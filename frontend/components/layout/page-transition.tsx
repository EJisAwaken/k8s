'use client'

// Composant repris d'EcoleManagement (design system commun).

import { usePathname } from 'next/navigation'

export function PageTransition({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  return (
    <div
      key={pathname}
      className="animate-in fade-in slide-in-from-bottom-2 duration-300 ease-out motion-reduce:animate-none print:animate-none"
    >
      {children}
    </div>
  )
}
