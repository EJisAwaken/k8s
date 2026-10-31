'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Page, PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Pagination } from '@/components/ui/pagination'
import { QueryState } from '@/components/ui/query-state'
import { useDebouncedValue } from '@/components/ui/search-field'
import { api } from '@/lib/api/client'
import type { AuditEntree, PageDto } from '@/lib/api/types'
import { horodatage } from '@/lib/format'

const ACTIONS = { INSERT: ['Création', 'green'], UPDATE: ['Modification', 'blue'], DELETE: ['Suppression', 'red'] } as const

export default function Audit() {
  const [f, setF] = useState({ entityName: '', action: '', performedBy: '' })
  const [page, setPage] = useState(0)
  const auteur = useDebouncedValue(f.performedBy, 300)
  const entites = useQuery({ queryKey: ['audit-entites'], queryFn: () => api.get<string[]>('/audit/entities') })
  const journal = useQuery({
    queryKey: ['audit', f.entityName, f.action, auteur, page],
    queryFn: () => api.get<PageDto<AuditEntree>>('/audit', { entityName: f.entityName, action: f.action, performedBy: auteur, page, size: 50 }),
    placeholderData: (prev) => prev,
  })
  const maj = (patch: Partial<typeof f>) => { setF({ ...f, ...patch }); setPage(0) }

  return (
    <Page>
      <PageHeader title="Journal d'audit" subtitle="Toute création, modification ou suppression : qui, quand, valeur avant / après." />
      <Card padding="sm" className="mb-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <Select label="Élément" value={f.entityName} onChange={(e) => maj({ entityName: e.target.value })}>
            <option value="">Tous</option>
            {entites.data?.map((e) => <option key={e} value={e}>{e}</option>)}
          </Select>
          <Select label="Action" value={f.action} onChange={(e) => maj({ action: e.target.value })}>
            <option value="">Toutes</option>
            {Object.entries(ACTIONS).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
          </Select>
          <Input label="Auteur" value={f.performedBy} onChange={(e) => maj({ performedBy: e.target.value })} placeholder="e-mail" />
        </div>
      </Card>
      <QueryState loading={journal.isLoading} error={journal.error} empty={journal.data?.content.length === 0} onRetry={() => void journal.refetch()}>
        <ul className="space-y-2">
          {journal.data?.content.map((a) => <Entree key={a.id} a={a} />)}
        </ul>
        {journal.data && (
          <Pagination page={journal.data.page} totalPages={journal.data.totalPages} totalElements={journal.data.totalElements}
            size={journal.data.size} onPageChange={setPage} label="entrée" labelPlural="entrées" />
        )}
      </QueryState>
    </Page>
  )
}

function Entree({ a }: { a: AuditEntree }) {
  const [libelle, ton] = ACTIONS[a.action]
  let changements: [string, unknown][] = []
  try {
    changements = a.changes ? Object.entries(JSON.parse(a.changes) as Record<string, unknown>) : []
  } catch { /* JSON illisible : on affiche sans détail */ }
  return (
    <li>
      <Card padding="sm">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge variant={ton}>{libelle}</Badge>
          <span className="font-medium">{a.entityName}</span>
          <span className="font-mono text-xs text-muted-foreground">#{a.entityId}</span>
          <span className="ml-auto text-xs text-muted-foreground">{a.performedBy} · {horodatage(a.performedAt)}</span>
        </div>
        {changements.length > 0 && (
          <details className="mt-2 text-xs">
            <summary className="cursor-pointer text-muted-foreground">{changements.length} champ(s)</summary>
            <table className="mt-2 w-full">
              <tbody className="divide-y divide-border">
                {changements.map(([champ, v]) => {
                  const diff = typeof v === 'object' && v !== null && 'new' in v ? (v as { old: unknown; new: unknown }) : null
                  return (
                    <tr key={champ}>
                      <td className="py-1 pr-3 font-mono text-muted-foreground">{champ}</td>
                      {diff ? (
                        <td className="py-1"><span className="text-destructive line-through">{String(diff.old ?? '∅')}</span> → <span className="text-success">{String(diff.new ?? '∅')}</span></td>
                      ) : (
                        <td className="py-1">{String(v ?? '∅')}</td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </details>
        )}
      </Card>
    </li>
  )
}
