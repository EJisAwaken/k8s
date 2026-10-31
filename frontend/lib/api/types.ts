/** Types des DTO du backend (miroir des records Java). Montants en ariary entiers. */

export interface PageDto<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── Catalogue
export interface Categorie {
  id: number
  nom: string
  parentId: number | null
  parentNom: string | null
  actif: boolean
  nbProduits: number
}

export interface Variante {
  id: number
  produitId: number
  sku: string
  attributs: string | null
  libelle: string
  codeBarres: string | null
  prixDetail: number | null
  prixGros: number | null
  prixRevendeur: number | null
  seuilGros: number | null
  seuilMini: number
  archive: boolean
  qtePhysique: number
  qteDisponible: number
}

export interface Produit {
  id: number
  reference: string
  nom: string
  nomFournisseur: string | null
  referenceFournisseur: string | null
  categorieId: number | null
  categorieNom: string | null
  unite: string
  poidsKg: number | null
  volumeM3: number | null
  codeSh: string | null
  suiviSerie: boolean
  description: string | null
  archive: boolean
  aDesMouvements: boolean
  variantes: Variante[]
}

export interface VarianteResume {
  id: number
  sku: string
  libelle: string
  codeBarres: string | null
  unite: string
  suiviSerie: boolean
  prixDetail: number | null
}

export interface VarianteForm {
  sku: string
  attributs: string
  codeBarres: string
  prixDetail: number | null
  prixGros: number | null
  prixRevendeur: number | null
  seuilGros: number | null
  seuilMini: number | null
}

// ── Dépôts
export interface Depot {
  id: number
  code: string
  nom: string
  adresse: string | null
  actif: boolean
}

export interface Emplacement {
  id: number
  depotId: number
  code: string
  libelle: string | null
  actif: boolean
}

// ── Stock
export type TypeMouvement =
  | 'STOCK_INITIAL' | 'ENTREE' | 'SORTIE' | 'TRANSFERT_SORTIE' | 'TRANSFERT_ENTREE'
  | 'AJUSTEMENT_POSITIF' | 'AJUSTEMENT_NEGATIF' | 'CASSE' | 'RETOUR'
  | 'INVENTAIRE_POSITIF' | 'INVENTAIRE_NEGATIF' | 'ANNULATION'

export interface TypeMouvementInfo {
  code: TypeMouvement
  label: string
  sens: number
  saisieDirecte: boolean
  motifObligatoire: boolean
}

export interface StockLigne {
  varianteId: number
  sku: string
  libelle: string
  unite: string
  depotId: number
  depotNom: string
  emplacementCode: string | null
  qteCommandee: number
  qteTransit: number
  qtePhysique: number
  qteReservee: number
  qteDisponible: number
  seuilMini: number
  cump: number | null
  valeur: number | null
  derniereSortieAt: string | null
}

export interface Mouvement {
  id: number
  numero: string
  type: TypeMouvement
  typeLabel: string
  sens: number
  varianteId: number
  sku: string
  libelle: string
  depotId: number
  depotNom: string
  quantite: number
  coutUnitaire: number | null
  valeur: number | null
  stockApres: number
  motif: string | null
  documentType: string | null
  documentId: number | null
  contrePasseId: number | null
  annule: boolean
  numerosSerie: string[]
  createdBy: string
  createdAt: string
}

export interface DocumentMouvement {
  numero: string
  mouvements: Mouvement[]
}

export interface Alertes {
  ruptures: StockLigne[]
  sousSeuil: StockLigne[]
  dormants: StockLigne[]
  joursDormance: number
}

export interface LigneMouvementForm {
  varianteId: number
  quantite: number
  coutUnitaire?: number | null
  numerosSerie?: string[]
}

// ── Inventaires
export type StatutInventaire = 'EN_COURS' | 'A_VALIDER' | 'VALIDE' | 'ANNULE'

export interface InventaireLigne {
  id: number
  varianteId: number
  sku: string
  libelle: string
  codeBarres: string | null
  suiviSerie: boolean
  qteTheorique: number
  qteComptee: number | null
  ecart: number | null
  valeurEcart: number | null
  compteAt: string | null
}

export interface Inventaire {
  id: number
  numero: string
  depotId: number
  depotNom: string
  type: 'COMPLET' | 'TOURNANT'
  statut: StatutInventaire
  commentaire: string | null
  nbLignes: number
  nbComptees: number
  nbEcarts: number
  valeurEcartPositif: number | null
  valeurEcartNegatif: number | null
  createdAt: string
  createdBy: string
  valideAt: string | null
  lignes: InventaireLigne[] | null
}

// ── Utilisateurs & audit
export interface Utilisateur {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: import('@/lib/auth/types').Role
  roleLabel: string
  active: boolean
  totpEnabled: boolean
  passwordChangeRequired: boolean
  createdAt: string
}

export interface UtilisateurCree {
  user: Utilisateur
  temporaryPassword: string
}

export interface AuditEntree {
  id: number
  entityName: string
  entityId: string | null
  action: 'INSERT' | 'UPDATE' | 'DELETE'
  changes: string | null
  performedBy: string | null
  performedAt: string
}

export interface SessionAppareil {
  id: number
  userAgent: string | null
  ipAddress: string | null
  createdAt: string
  lastUsedAt: string
  current: boolean
}

export interface RapportImport {
  applique: boolean
  simulation: boolean
  lignesLues: number
  crees: number
  misAJour: number
  numeroDocument: string | null
  erreurs: { ligne: number; message: string }[]
}
