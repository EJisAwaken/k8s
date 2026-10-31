# Stock Import — Lot 1 « Socle »

Gestion de stock, import Chine et suivi clients (voir le cahier des charges dans ce dossier).
Ce dépôt contient le **lot 1** : utilisateurs et rôles, catalogue, dépôts, stock et mouvements,
inventaires, reprise de données, étiquettes.

| Couche | Stack | Origine |
| --- | --- | --- |
| `backend/` | Spring Boot 3.5 · Java 21 · PostgreSQL · Flyway · JWT | socle transverse repris de **Gestion-H** (sécurité, audit, exceptions, export Excel, QR) |
| `frontend/` | Next.js 16 · React 19 · Tailwind 4 · TanStack Query | design system et composants repris d'**EcoleManagement** ; authentification BFF à cookies httpOnly reprise de **Gestion-H** |

> **DevOps volontairement absent** (Dockerfile, docker-compose, CI/CD, reverse proxy TLS,
> supervision, sauvegardes) : cette partie est réservée à la formation des stagiaires.

## Démarrer en local

### 1. Base PostgreSQL (16 recommandé)

```sql
CREATE USER stock_user WITH PASSWORD 'stock_dev_pass';
CREATE DATABASE stock_db OWNER stock_user;
CREATE DATABASE stock_test OWNER stock_user;   -- pour les tests
```

Par défaut le backend attend la base sur `localhost:5440`. Pour une autre adresse :
`DB_URL=jdbc:postgresql://localhost:5432/stock_db`. Le schéma est créé par Flyway au démarrage
(l'extension `pg_trgm` est nécessaire : le propriétaire de la base peut la créer depuis PG 13).

### 2. Backend — http://localhost:8095

```bash
cd backend
./mvnw spring-boot:run
```

Au premier démarrage, un administrateur est créé : `admin@stock.mg` / `Admin12345`
(mot de passe temporaire à changer à la première connexion ; 2FA demandée ensuite).
API documentée sur http://localhost:8095/swagger-ui.html.

Variables utiles : `APP_JWT_SECRET` (≥ 32 octets, **obligatoire hors poste de dev**),
`APP_BOOTSTRAP_ADMIN_PASSWORD` (obligatoire en profil `prod`), `APP_CORS_ORIGINS`,
`APP_COOKIE_SECURE=true` derrière HTTPS, `APP_STOCK_DORMANT_DAYS` (90 par défaut).

Tests (base `stock_test`) : `./mvnw test`.

### 3. Frontend — http://localhost:3010

```bash
cd frontend
npm install
npm run dev -- -p 3010
```

Le navigateur n'appelle que le frontend : `/api/v1/*` est relayé vers le backend
(`BACKEND_INTERNAL_URL`, défaut `http://localhost:8095`) et le jeton JWT reste dans un cookie
httpOnly, jamais accessible au JavaScript.

## Ce que couvre le lot 1

- **Rôles** (cahier §2) : administrateur, responsable achats (ne fixe pas les prix de vente),
  magasinier (ne voit ni coûts ni marges), commercial (ne modifie pas le stock), comptable
  (lecture seule, imposée globalement par `ReadOnlyRoleFilter`).
- **Sécurité** : bcrypt, JWT 15 min + refresh token tourné, anti-force-brute, 2FA TOTP
  obligatoire pour l'administrateur, journal d'audit avant/après de toute entité.
- **Catalogue** : produits, variantes, codes-barres (EAN-13 interne généré), grille de prix
  détail/gros/revendeur, suivi par n° de série / IMEI. **RG-01** : suppression impossible
  après mouvement, archivage seulement.
- **Stock** : quatre quantités par (variante, dépôt), CUMP recalculé à chaque entrée (RG-04),
  mouvements immuables — trigger PostgreSQL (**RG-05**), jamais de physique négatif — contrôle
  sous verrou + contrainte en base (**RG-06**), transferts, annulation par mouvement inverse,
  alertes (rupture, seuil, dormant), exports Excel.
- **Inventaires** complets ou tournants, comptage par scan, rapport d'écarts validé par
  l'administrateur.
- **Reprise** : import Excel du catalogue et du stock initial valorisé (tout ou rien, mode
  « vérifier sans importer »).
- **Étiquettes** PDF : planche A4 3×8 ou rouleau thermique 50×30 mm, code 128.

Critères de recette du lot 1 couverts : inventaire initial importé ; entrée, sortie et
transfert tracés ; RG-05 et RG-06 vérifiées (`StockServiceTest`).

## Lots suivants

Les quantités « en commande », « en transit » et « réservée » existent déjà dans le modèle :
elles seront alimentées par les lots 2 (ventes, réservations) et 3 (import Chine, landed cost).
