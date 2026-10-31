// Composant repris d'EcoleManagement (design system commun).
import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

const PADDING = { none: '', sm: 'p-4', md: 'p-5', lg: 'p-6' } as const

export interface CardProps extends React.HTMLAttributes<HTMLElement> {
  padding?: keyof typeof PADDING
  as?: 'div' | 'section' | 'article' | 'aside'
}

export const Card = forwardRef<HTMLElement, CardProps>(
  function Card({ className, padding = 'md', as: Tag = 'div', ...props }, ref) {
    return (
      <Tag
        ref={ref as React.Ref<HTMLDivElement>}
        className={cn('rounded-xl bg-card shadow-sm ring-1 ring-border print:break-inside-avoid',
          PADDING[padding], className)}
        {...props}
      />
    )
  },
)
