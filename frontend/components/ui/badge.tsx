'use client'

// Composant repris d'EcoleManagement (design system commun).

import { clsx } from 'clsx'

interface BadgeProps {
  children: React.ReactNode
  variant?: 'gray' | 'green' | 'red' | 'blue' | 'yellow'
  className?: string
}

export function Badge({ children, variant = 'gray', className }: BadgeProps) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        {
          'bg-muted text-muted-foreground': variant === 'gray',
          'bg-success/5 text-success': variant === 'green',
          'bg-red-500/15 text-red-700 dark:text-red-400': variant === 'red',
          'bg-blue-500/15 text-blue-700 dark:text-blue-400': variant === 'blue',
          'bg-yellow-500/15 text-yellow-700 dark:text-yellow-400': variant === 'yellow',
        },
        className,
      )}
    >
      {children}
    </span>
  )
}
