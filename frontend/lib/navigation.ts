import {
  ArrowLeftRight, Barcode, Boxes, ClipboardCheck, FileSpreadsheet, History, LayoutDashboard,
  Package, ShieldCheck, TriangleAlert, Users, Warehouse, type LucideIcon,
} from 'lucide-react'
import type { Role } from '@/lib/auth/types'

export interface MenuItem {
  href: string
  label: string
  icon: LucideIcon
  /** Rôles qui voient l'entrée ; absent = tous. Le backend reste seul juge des droits. */
  roles?: Role[]
}

export interface MenuGroup {
  label: string
  items: MenuItem[]
}

/** Menus du lot 1 — les lots suivants (ventes, import Chine, CRM) s'ajouteront ici. */
export const MENUS: MenuGroup[] = [
  {
    label: '',
    items: [{ href: '/', label: 'Tableau de bord', icon: LayoutDashboard }],
  },
  {
    label: 'Stock',
    items: [
      { href: '/stock', label: 'État du stock', icon: Boxes },
      { href: '/stock/mouvements', label: 'Mouvements', icon: ArrowLeftRight },
      { href: '/stock/alertes', label: 'Alertes', icon: TriangleAlert },
      { href: '/inventaires', label: 'Inventaires', icon: ClipboardCheck },
    ],
  },
  {
    label: 'Catalogue',
    items: [
      { href: '/catalogue', label: 'Produits', icon: Package },
      { href: '/etiquettes', label: 'Étiquettes', icon: Barcode, roles: ['ADMIN', 'MAGASINIER', 'ACHATS', 'COMMERCIAL'] },
    ],
  },
  {
    label: 'Administration',
    items: [
      { href: '/depots', label: 'Dépôts', icon: Warehouse },
      { href: '/utilisateurs', label: 'Utilisateurs', icon: Users, roles: ['ADMIN'] },
      { href: '/reprise', label: 'Reprise de données', icon: FileSpreadsheet, roles: ['ADMIN'] },
      { href: '/audit', label: "Journal d'audit", icon: History, roles: ['ADMIN', 'COMPTABLE'] },
      { href: '/profil', label: 'Mon compte', icon: ShieldCheck },
    ],
  },
]

export function menusPour(role: Role): MenuGroup[] {
  return MENUS.map((g) => ({ ...g, items: g.items.filter((i) => !i.roles || i.roles.includes(role)) }))
    .filter((g) => g.items.length > 0)
}

export function estActif(href: string, pathname: string): boolean {
  return href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`)
}
