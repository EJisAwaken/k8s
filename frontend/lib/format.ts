/**
 * Formats du cahier (§6) : montants en ariary sans décimales (« 1 234 500 Ar »),
 * devises étrangères à 2 décimales, dates JJ/MM/AAAA.
 */

const nombre = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 3 })
const ariary = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })
const date = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateHeure = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
})

/** « 1 234 500 Ar » ; « — » si la valeur est absente (ou masquée pour le profil). */
export function mga(v: number | null | undefined): string {
  return v == null ? '—' : `${ariary.format(Math.round(v))}\u00a0Ar`
}

/** Quantité : jusqu'à 3 décimales, séparateur de milliers français. */
export function qte(v: number | null | undefined): string {
  return v == null ? '—' : nombre.format(v)
}

export function jour(iso: string | null | undefined): string {
  return iso ? date.format(new Date(iso)) : '—'
}

export function horodatage(iso: string | null | undefined): string {
  return iso ? dateHeure.format(new Date(iso)) : '—'
}

/** Date du jour au format ISO (AAAA-MM-JJ) dans le fuseau du navigateur. */
export function aujourdhuiIso(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
