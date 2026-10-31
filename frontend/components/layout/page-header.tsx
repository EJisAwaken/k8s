/** En-tête de page : titre, sous-titre et actions alignées à droite. */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: React.ReactNode
  actions?: React.ReactNode
}) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 print:hidden">{actions}</div>}
    </div>
  )
}

/** Conteneur de page standard. */
export function Page({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto w-full max-w-7xl p-4 md:p-6 lg:p-8">{children}</div>
}
