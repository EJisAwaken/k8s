/**
 * Couche i18n minimale, compatible avec l'API `useT()` des composants repris
 * d'EcoleManagement. Le produit est en français seul (cahier §6) : un unique
 * catalogue, et `t()` retombe sur la clé si un libellé manque — à corriger ici.
 */

export type Translator = (key: string, vars?: Record<string, string | number>) => string

const fr: Record<string, string> = {
  'act.close': 'Fermer',
  'ds.cancel': 'Annuler',
  'ds.choose': '— Choisir —',
  'ds.close': 'Fermer',
  'ds.columns': 'Colonnes',
  'ds.columnsHidden': 'Colonnes ({n} masquée(s))',
  'ds.confirm': 'Confirmer',
  'ds.empty': 'Aucune donnée.',
  'ds.help': 'Aide',
  'ds.noResults': 'Aucun résultat',
  'ds.search': 'Rechercher…',
  'ds.showAll': 'Tout réafficher',
  'ds.showPassword': 'Afficher le mot de passe',
  'ds.hidePassword': 'Masquer le mot de passe',
  'qs.empty': 'Aucune donnée.',
  'qs.loadError': 'Chargement impossible.',
  'qs.retry': 'Réessayer',
  'tmp.copied': 'Copié',
  'tmp.copy': 'Copier',
  'tmp.intro':
    'Communiquez ce mot de passe temporaire à la personne ; il devra être changé à la première connexion.',
  'tmp.label': 'Mot de passe temporaire',
  'tmp.note': 'Il ne sera plus affiché. À changer à la première connexion.',
  'tmp.title': 'Mot de passe temporaire',
}

function interpoler(s: string, vars?: Record<string, string | number>): string {
  if (!vars) return s
  return Object.entries(vars).reduce((acc, [k, v]) => acc.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v)), s)
}

export const t: Translator = (key, vars) => interpoler(fr[key] ?? key, vars)

/** Libellé de la clé, ou repli français fourni par l'appelant si la clé est inconnue. */
export function resolveWith(
  translator: Translator,
  key: string,
  fallbackFr: string,
  vars?: Record<string, string | number>,
): string {
  const s = translator(key, vars)
  return s === key ? interpoler(fallbackFr, vars) : s
}
