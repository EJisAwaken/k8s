# Cahier des charges — Gestion de stock, import Chine et suivi clients

Oct 15, 2026 · @Esoalahy Josefa

## 1. Contexte et objectifs

L'application centralise trois flux aujourd'hui suivis à la main : les achats importés de Chine, le stock, et les commandes et relations clients. Elle doit donner à tout moment le stock réel, le coût de revient réel de chaque produit, et l'état de chaque commande et de chaque client.

**Problèmes à résoudre**

- Coût de revient inconnu : le prix fournisseur ne reflète pas le fret, la douane et les frais locaux.
- Stock « en transit » invisible : la marchandise payée mais pas encore arrivée n'est suivie nulle part.
- Ventes sur stock inexistant : des commandes clients sont acceptées sans réservation de stock.
- Suivi client dispersé entre WhatsApp, Facebook et carnets : pas d'historique, pas de relance, pas de suivi des créances.

**Objectifs mesurables**

| Objectif | Indicateur | Cible |
| --- | --- | --- |
| Stock fiable | Écart inventaire physique / système | < 2 % en valeur |
| Marge connue | Produits avec coût de revient calculé (landed cost) | 100 % |
| Aucune survente | Commandes confirmées sans stock disponible ou en transit | 0 |
| Suivi client | Commandes avec statut à jour et client notifié | 100 % |
| Créances maîtrisées | Encours clients en retard de plus de 30 jours | Visible chaque jour |

Hypothèses retenues (à valider) : activité basée à Madagascar, monnaie de référence Ariary (MGA), achats facturés en CNY ou USD, ventes B2C et B2B, un ou deux dépôts au démarrage.

## 2. Périmètre et utilisateurs

La version 1 couvre l'achat import, le stock, la vente et le suivi client. La comptabilité générale, la paie et la boutique en ligne publique sont hors périmètre ; l'application exporte les données nécessaires au comptable.

| Dans le périmètre (V1) | Hors périmètre (V1) |
| --- | --- |
| Catalogue produits, variantes, codes-barres | Comptabilité générale, bilan, TVA déclarative |
| Fournisseurs chinois, bons de commande, conteneurs/colis | Paie et RH |
| Calcul du coût de revient (landed cost) | Site e-commerce public |
| Stock multi-dépôts, mouvements, inventaires | Intégration API 1688 / Alibaba |
| Commandes clients, réservations, livraisons, retours | Application mobile native (le web est responsive) |
| Fiches clients, historique, relances, créances |  |
| Paiements : espèces, Mobile Money, virement, acomptes |  |
| Tableaux de bord et exports Excel/PDF |  |

**Rôles utilisateurs**

| Rôle | Peut faire | Ne peut pas faire |
| --- | --- | --- |
| Administrateur | Tout, dont utilisateurs, paramètres, taux de change | — |
| Responsable achats | Fournisseurs, bons de commande, suivi expéditions, frais d'import | Modifier les prix de vente |
| Magasinier | Réceptions, transferts, préparation, inventaires | Voir les coûts d'achat et les marges |
| Commercial | Clients, devis, commandes, encaissements, relances | Modifier le stock directement |
| Comptable / gérant (lecture) | Rapports, marges, créances, exports | Saisir ou modifier des opérations |

Option V2 : un portail client en lecture seule pour suivre ses commandes et son solde.

## 3. Exigences fonctionnelles

Sept modules, chacun avec ses règles de gestion numérotées (RG) pour la recette.

### 3.1 Catalogue produits

- Fiche produit : référence interne, nom FR, nom/référence fournisseur chinois, catégorie, photos, unité, poids et volume (pour répartir le fret), code SH douanier.
- Variantes (couleur, taille, modèle) avec un stock propre par variante.
- Code-barres ou QR généré et imprimable en étiquettes.
- Prix de vente par grille : détail, gros, revendeur ; seuil de quantité pour le prix de gros.
- RG-01 : un produit avec mouvements de stock ne peut pas être supprimé, seulement archivé.

### 3.2 Approvisionnement Chine

- Fournisseurs : nom, plateforme (1688, Taobao, Alibaba, marché physique), contact WeChat, devise, délai moyen, note qualité.
- Transitaires / agents : tarif par kg ou par m³, mode (aérien, maritime groupé, conteneur).
- Bon de commande fournisseur : lignes produits, prix en CNY ou USD, acomptes versés, statut.
- Expédition : regroupe un ou plusieurs bons de commande dans un colis ou conteneur, avec numéro de suivi, date de départ, ETA, port ou aéroport d'arrivée.
- Frais d'import saisis par expédition : fret, droits et taxes de douane, transitaire local, transport jusqu'au dépôt, frais bancaires de transfert.
- RG-02 : chaque achat enregistre le taux de change du jour du paiement ; il n'est plus recalculé ensuite.
- RG-03 : le coût de revient unitaire = prix d'achat converti en MGA + quote-part des frais de l'expédition. La répartition des frais se fait au choix par valeur, par poids ou par volume, paramétrable par expédition.
- RG-04 : la valorisation du stock utilise le coût moyen pondéré (CUMP), recalculé à chaque réception.

### 3.3 Stock

- Quatre quantités par produit et par dépôt : en commande fournisseur, en transit, physique, réservée. Disponible = physique − réservée.
- Réception d'une expédition avec contrôle des quantités : manquants et produits abîmés enregistrés séparément, avec motif.
- Mouvements tracés : entrée, sortie, transfert entre dépôts, ajustement, casse, retour. Chaque mouvement porte l'utilisateur, la date et le document source.
- Emplacements optionnels dans un dépôt (rayon, étagère).
- Inventaire complet ou tournant, saisie par scan, rapport d'écarts à valider par l'administrateur.
- Alertes : seuil minimum, stock dormant (aucune vente depuis N jours), rupture.
- Suivi par numéro de série ou IMEI activable par produit (téléphones, électronique) pour la garantie.
- RG-05 : aucun mouvement ne peut être modifié ou supprimé ; une erreur se corrige par un mouvement inverse.
- RG-06 : le stock physique ne peut jamais devenir négatif.

### 3.4 Commandes clients

- Canaux : boutique, téléphone, WhatsApp, Facebook ; le canal est enregistré sur la commande.
- Devis convertible en commande, commande convertible en facture.
- Précommande sur marchandise en transit, avec acompte et date de disponibilité estimée reprise de l'ETA.
- Statuts : Brouillon → Confirmée → En préparation → Prête → Livrée (ou Retirée) → Clôturée ; Annulée possible avant livraison.
- Livraison : adresse, livreur, frais de livraison, preuve (photo ou signature).
- Retours et échanges avec remise en stock ou passage en casse.
- Documents PDF : devis, bon de commande, bon de livraison, facture, reçu.
- RG-07 : la confirmation d'une commande réserve le stock disponible ; sans stock disponible, seule une précommande sur stock en transit est possible.
- RG-08 : remise au-delà d'un plafond paramétrable (ex. 10 %) soumise à validation de l'administrateur.

### 3.5 Suivi clients (CRM)

- Fiche client : particulier ou entreprise, téléphones, WhatsApp, adresse, NIF/STAT pour les entreprises, catégorie de prix, plafond de crédit.
- Historique complet : commandes, paiements, retours, produits achetés, panier moyen, date du dernier achat.
- Journal d'interactions : appel, message, visite, réclamation, avec date et auteur.
- Tâches de relance avec échéance assignées à un commercial.
- Segmentation : nouveaux, réguliers, inactifs depuis 90 jours, revendeurs, mauvais payeurs.
- Notifications au client par SMS ou WhatsApp : commande confirmée, arrivée de sa précommande, prête, livrée, rappel de paiement.
- RG-09 : un client qui dépasse son plafond de crédit ne peut pas passer de nouvelle commande à crédit sans validation.

### 3.6 Paiements et créances

- Modes : espèces, MVola, Orange Money, Airtel Money, virement, chèque.
- Paiements partiels et acomptes, avec référence de transaction Mobile Money.
- Balance âgée des créances : 0–30, 31–60, plus de 60 jours.
- Clôture de caisse journalière par utilisateur, avec écart constaté.
- Suivi des paiements aux fournisseurs et des acomptes en cours.

### 3.7 Tableaux de bord et rapports

- Tableau de bord : chiffre d'affaires du jour et du mois, marge brute, valeur du stock, expéditions en transit, commandes en attente, créances en retard.
- Rapports : ventes par produit, client, commercial et canal ; marge par produit et par expédition ; rotation du stock ; produits dormants ; historique des coûts d'import.
- Exports Excel et PDF ; filtres par période et dépôt.

## 4. Workflows principaux

Le stock n'est jamais saisi à la main : chaque document d'achat ou de vente le met à jour automatiquement.

&#91;embedded content: flux import et vente · effet de chaque étape sur le stock\]

Les étapes du haut alimentent le stock commandé puis en transit ; celles du bas réservent puis sortent le stock physique.

**Cas particuliers**

- Précommande : une commande peut réserver du stock en transit ; elle bascule sur le stock physique à la réception de l'expédition et le client est notifié.
- Écart à la réception : les manquants restent une réclamation ouverte chez le fournisseur ou le transitaire ; les articles abîmés passent en casse.
- Annulation avant livraison : la réservation est libérée, l'acompte est remboursé ou converti en avoir.
- Retour client : contrôle, puis remise en stock ou casse, et avoir ou remboursement.

## 5. Modèle de données principal

Quinze entités suffisent pour la V1 ; le stock se déduit des mouvements, jamais d'un champ modifiable à la main.

| Entité | Champs clés | Relations |
| --- | --- | --- |
| Produit | référence, nom, catégorie, poids, volume, code SH, suivi série oui/non | 1–n Variante |
| Variante | SKU, attributs, code-barres, prix détail / gros / revendeur, seuil mini | n–1 Produit |
| Dépôt | nom, adresse, emplacements | 1–n Stock |
| Stock | variante, dépôt, qté commandée, en transit, physique, réservée, CUMP | clé unique (variante, dépôt) |
| Mouvement de stock | type, quantité, coût unitaire, document source, utilisateur, date | n–1 Variante, n–1 Dépôt |
| Fournisseur | nom, plateforme, WeChat, devise, délai moyen | 1–n Bon de commande |
| Bon de commande fournisseur | numéro, date, devise, taux de change, statut, acomptes | 1–n Ligne BC ; n–1 Expédition |
| Expédition | transitaire, mode, n° de suivi, départ, ETA, arrivée, méthode de répartition | 1–n BC ; 1–n Frais d'import |
| Frais d'import | type (fret, douane, transitaire, transport, banque), montant, devise, taux | n–1 Expédition |
| Client | type, nom, téléphones, WhatsApp, NIF/STAT, catégorie de prix, plafond crédit | 1–n Commande, 1–n Interaction |
| Commande client | numéro, canal, statut, remise, frais de livraison, commercial | 1–n Ligne ; 1–n Paiement ; 0–n Livraison |
| Livraison | date, livreur, adresse, preuve | n–1 Commande |
| Paiement | mode, montant, référence Mobile Money, date, caisse | n–1 Commande ou Fournisseur |
| Interaction / relance | type, note, échéance, statut, auteur | n–1 Client |
| Journal d'audit | entité, action, valeur avant, valeur après, utilisateur, date | toutes les entités |

Utilisateur, Rôle, Taux de change et Numérotation des documents complètent le modèle comme tables de paramétrage.

## 6. Exigences non fonctionnelles et architecture

Application web responsive, utilisable sur PC, tablette et smartphone, hébergée sur un serveur unique avec sauvegardes externalisées.

| Exigence | Critère |
| --- | --- |
| Disponibilité | 99 % sur les heures d'ouverture ; coupures électriques locales sans impact (hébergement VPS) |
| Connexion faible | Pages < 500 Ko, utilisables en 3G ; scan et saisie de vente tolérants à une coupure brève (file locale puis synchronisation) |
| Performance | Recherche produit < 1 s sur 20 000 références ; rapport mensuel < 5 s |
| Sécurité | HTTPS, mots de passe hachés (bcrypt/argon2), droits par rôle, 2FA pour l'administrateur |
| Traçabilité | Journal d'audit de toute création, modification, annulation : qui, quand, avant/après |
| Sauvegarde | Dump base quotidien chiffré hors serveur, conservation 30 jours, test de restauration mensuel |
| Langue et formats | Français ; montants en MGA sans décimales, devises étrangères à 2 décimales ; dates JJ/MM/AAAA |
| Matériel | Douchette code-barres USB/Bluetooth, imprimante thermique 80 mm pour reçus, imprimante d'étiquettes |

**Architecture proposée**

| Couche | Choix | Raison |
| --- | --- | --- |
| Frontend | Next.js (React), PWA | Responsive, installable sur mobile, cache hors ligne partiel |
| Backend | Spring Boot, API REST, OpenAPI | Transactions fiables pour stock et paiements, typage fort |
| Base de données | PostgreSQL | Intégrité transactionnelle, verrous pour les réservations de stock |
| Fichiers | Stockage objet compatible S3 (MinIO) | Photos produits, preuves de livraison, PDF |
| Notifications | Passerelle SMS locale et API WhatsApp Business | Avis de commande et relances |
| Déploiement | Docker Compose, reverse proxy avec TLS, CI/CD | Mise à jour sans interruption longue, environnements test et prod |
| Supervision | Uptime monitoring + logs centralisés | Alerte en cas de panne ou d'erreur répétée |

Alternative à arbitrer : un ERP open source paramétré (Odoo, Dolibarr) livre 70 à 80 % de ces fonctions plus vite, mais le calcul du coût d'import par expédition et le suivi en transit demanderont des modules spécifiques, et la personnalisation y est plus coûteuse à maintenir. Le développement sur mesure coûte plus au départ mais colle exactement au flux import Chine.

## 7. Planning, livrables et recette

Livraison en quatre lots sur environ 16 semaines pour une équipe de 2 développeurs ; chaque lot est utilisable en production avant le suivant.

| Lot | Contenu | Durée estimée | Critère de recette |
| --- | --- | --- | --- |
| 1 — Socle | Utilisateurs et rôles, catalogue, dépôts, stock et mouvements, inventaire initial, étiquettes | 4 semaines | Inventaire initial importé ; une entrée, une sortie et un transfert tracés ; RG-05 et RG-06 vérifiées |
| 2 — Vente et clients | Fiches clients, devis, commandes, réservations, livraisons, paiements, caisse, PDF | 4 semaines | Une commande payée en deux fois avec MVola, livrée, stock décrémenté ; RG-07 à RG-09 vérifiées |
| 3 — Import Chine | Fournisseurs, bons de commande, expéditions, frais, landed cost, réception, précommandes | 4 semaines | Une expédition réelle saisie : coût de revient recalculé à l'ariary près face à un calcul Excel de contrôle |
| 4 — Pilotage | CRM (interactions, relances, segments), notifications SMS/WhatsApp, tableaux de bord, rapports, exports | 4 semaines | Tableau de bord cohérent avec les rapports ; notification reçue à chaque changement de statut |

**Livrables** : code source sur dépôt Git, documentation d'installation et d'exploitation, guide utilisateur par rôle, scripts de sauvegarde et restauration, formation de 2 demi-journées, garantie corrective de 3 mois après le lot 4.

**Reprise des données** : import Excel/CSV des produits, du stock initial valorisé et des clients avec leur solde dû.

**Points à valider avant le lancement**

- [ ] Nombre de dépôts ou boutiques, et nombre d'utilisateurs simultanés
- [ ] Volume : nombre de références, de commandes par jour, d'expéditions par mois
- [ ] Méthode de répartition des frais d'import par défaut : valeur, poids ou volume
- [ ] Facturation avec TVA ou non (régime fiscal de l'entreprise) et mentions légales des factures
- [ ] Ventes à crédit autorisées et plafonds par catégorie de client
- [ ] Opérateur SMS et compte WhatsApp Business à utiliser
- [ ] Développement sur mesure ou ERP paramétré (section 6)
- [ ] Budget et date de mise en service souhaitée
