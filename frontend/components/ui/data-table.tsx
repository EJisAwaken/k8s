'use client'

// Composant repris d'EcoleManagement (design system commun).

import {
  type ColumnDef,
  type OnChangeFn,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { useState } from 'react'
import { ArrowDown, ArrowUp, ArrowUpDown, Columns3 } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { QueryErrorNotice } from '@/components/ui/query-state'
import { usePersistedState } from '@/lib/use-persisted'
import { useT } from '@/providers/locale-provider'

const NO_HIDDEN: string[] = []

export function DataTable<T, K extends string | number = string | number>({
  columns,
  data,
  empty,
  emptyAction,
  error,
  onRetry,
  sorting,
  onSortingChange,
  manualSorting = false,
  loading = false,
  skeletonRows = 8,
  tableId,
  selection,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: ColumnDef<T, any>[]
  data: T[]
  empty?: string
  emptyAction?: React.ReactNode
  /** Erreur de la requête ; passer `isError ? error : null`, ou `null` si aucune requête. */
  error: unknown
  /** Relance de la requête ; sans elle, l'erreur est nommée mais pas rejouable. */
  onRetry?: () => void
  sorting?: SortingState
  onSortingChange?: OnChangeFn<SortingState>
  manualSorting?: boolean
  loading?: boolean
  skeletonRows?: number
  /** Identifiant stable pour mémoriser les colonnes masquées (active le menu « Colonnes »). */
  tableId?: string
  selection?: {
    selectedIds: Set<K>
    getRowId: (row: T) => K
    onToggle: (id: K) => void
    /** Bascule « tout / rien » pour les lignes actuellement affichées (`data`). */
    onToggleAll: () => void
    selectAllAria: string
    selectOneAria: (row: T) => string
  }
}) {
  const t = useT()
  const [internalSorting, setInternalSorting] = useState<SortingState>([])
  const [hidden, setHidden] = usePersistedState<string[]>(
    tableId ? `table.${tableId}.hidden` : 'table.__aucune.hidden', NO_HIDDEN)
  const [pickerOpen, setPickerOpen] = useState(false)

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting: sorting ?? internalSorting,
      columnVisibility: Object.fromEntries(hidden.map(id => [id, false])),
    },
    onSortingChange: onSortingChange ?? setInternalSorting,
    manualSorting,
    getCoreRowModel: getCoreRowModel(),
    ...(manualSorting ? {} : { getSortedRowModel: getSortedRowModel() }),
  })

  // Colonnes proposées au masquage : en-tête textuel uniquement.
  const hideable = table.getAllLeafColumns()
    .filter(c => typeof c.columnDef.header === 'string' && (c.columnDef.header as string).length > 0)
  const visibleCount = hideable.filter(c => !hidden.includes(c.id)).length

  const allChecked = !!selection && data.length > 0 && data.every(d => selection.selectedIds.has(selection.getRowId(d)))
  const someChecked = !!selection && !allChecked && data.some(d => selection.selectedIds.has(selection.getRowId(d)))
  const colSpan = table.getVisibleLeafColumns().length + (selection ? 1 : 0)

  return (
    <div>
      {tableId && hideable.length > 1 && (
        <div className="relative flex justify-end px-2 py-1.5 print:hidden">
          <button
            type="button"
            onClick={() => setPickerOpen(o => !o)}
            aria-expanded={pickerOpen}
            aria-haspopup="true"
            className="inline-flex items-center justify-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring pointer-coarse:min-h-[44px] pointer-coarse:min-w-[44px]"
          >
            <Columns3 size={13} /> {hidden.length > 0 ? t('ds.columnsHidden', { n: hidden.length }) : t('ds.columns')}
          </button>
          {pickerOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setPickerOpen(false)} aria-hidden />
              <div className="absolute right-2 top-8 z-50 w-52 rounded-xl border border-border bg-popover p-1.5 shadow-lg">
                {hideable.map(col => {
                  const isHidden = hidden.includes(col.id)
                  const lastVisible = !isHidden && visibleCount === 1
                  return (
                    <label key={col.id}
                      className={`flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm hover:bg-muted pointer-coarse:min-h-[44px] ${lastVisible ? 'cursor-not-allowed opacity-50' : ''}`}>
                      <input
                        type="checkbox"
                        checked={!isHidden}
                        disabled={lastVisible}
                        onChange={() => setHidden(prev =>
                          prev.includes(col.id) ? prev.filter(id => id !== col.id) : [...prev, col.id])}
                        className="h-3.5 w-3.5 rounded border-input accent-primary"
                      />
                      <span className="text-foreground">{String(col.columnDef.header)}</span>
                    </label>
                  )
                })}
                {hidden.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setHidden(NO_HIDDEN)}
                    className="mt-1 flex w-full items-center rounded-lg border-t border-border px-2.5 py-1.5 text-left text-xs text-muted-foreground hover:bg-muted hover:text-foreground pointer-coarse:min-h-[44px]"
                  >
                    {t('ds.showAll')}
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      )}
      <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-border bg-muted text-xs uppercase tracking-wide text-muted-foreground">
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id}>
              {selection && (
                <th scope="col" className="w-10 px-4 py-2.5">
                  <input type="checkbox" aria-label={selection.selectAllAria}
                    checked={allChecked}
                    ref={el => { if (el) el.indeterminate = someChecked }}
                    onChange={selection.onToggleAll}
                    className="h-4 w-4 rounded border-input" />
                </th>
              )}
              {hg.headers.map(header => {
                const sortable = header.column.getCanSort()
                const dir = header.column.getIsSorted()
                return (
                  <th
                    key={header.id}
                    scope="col"
                    aria-sort={dir === 'asc' ? 'ascending' : dir === 'desc' ? 'descending' : sortable ? 'none' : undefined}
                    className={sortable ? 'px-4 py-2.5 pointer-coarse:py-0 font-semibold' : 'px-4 py-2.5 font-semibold'}
                  >
                    {header.isPlaceholder ? null : sortable ? (
                      <button
                        type="button"
                        onClick={header.column.getToggleSortingHandler()}
                        // Le tri de colonne ne mesurait que la hauteur de son texte
                        // (~16 px) alors que c'est le geste le plus courant sur une
                        // liste. Plancher tactile en PIXELS (cf. `button.tsx`).
                        className="-mx-1 inline-flex items-center gap-1 rounded px-1 transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring pointer-coarse:min-h-[44px]"
                      >
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {dir === 'asc' ? <ArrowUp size={12} /> : dir === 'desc' ? <ArrowDown size={12} /> : <ArrowUpDown size={12} className="opacity-40" />}
                      </button>
                    ) : (
                      flexRender(header.column.columnDef.header, header.getContext())
                    )}
                  </th>
                )
              })}
            </tr>
          ))}
        </thead>
        <tbody className="divide-y divide-border">
          {loading && Array.from({ length: skeletonRows }).map((_, r) => (
            <tr key={`sk-${r}`}>
              {selection && (
                <td className="px-4 py-3"><Skeleton className="h-4 w-4" /></td>
              )}
              {columns.map((_c, i) => (
                <td key={i} className="px-4 py-3">
                  <Skeleton className="h-4" style={{ width: `${60 + ((i * 13 + r * 7) % 35)}%` }} />
                </td>
              ))}
            </tr>
          ))}
          {!loading && table.getRowModel().rows.map(row => (
            <tr key={row.id} className="align-top hover:bg-muted">
              {selection && (
                <td className="px-4 py-2.5">
                  <input type="checkbox" aria-label={selection.selectOneAria(row.original)}
                    checked={selection.selectedIds.has(selection.getRowId(row.original))}
                    onChange={() => selection.onToggle(selection.getRowId(row.original))}
                    className="h-4 w-4 rounded border-input" />
                </td>
              )}
              {row.getVisibleCells().map(cell => (
                <td key={cell.id} className="px-4 py-2.5">
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
          {!loading && error != null && (
            <tr>
              <td colSpan={colSpan} className="px-4 py-4">
                <QueryErrorNotice error={error} onRetry={onRetry} />
              </td>
            </tr>
          )}
          {!loading && error == null && data.length === 0 && (
            <tr>
              <td colSpan={colSpan} className="px-4 py-8 text-center text-muted-foreground">
                <div className="flex flex-col items-center gap-3">
                  <span>{empty ?? t('ds.empty')}</span>
                  {emptyAction}
                </div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
      </div>
    </div>
  )
}
