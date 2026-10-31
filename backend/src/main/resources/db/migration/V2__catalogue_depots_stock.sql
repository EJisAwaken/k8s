-- ═══════════════════════════════════════════════════════════════════════════
-- V2 — Lot 1 : catalogue, dépôts, stock, mouvements, numéros de série,
-- inventaires.
--
-- Montants en ariary : BIGINT (MGA sans décimales, cahier §6).
-- Coût moyen pondéré (CUMP) : NUMERIC(18,4) — la précision se garde dans le
-- calcul, l'arrondi à l'ariary se fait à la valorisation.
-- Quantités : NUMERIC(14,3) (pièces, mais aussi kg ou mètres).
-- ═══════════════════════════════════════════════════════════════════════════

-- Recherche produit < 1 s sur 20 000 références (cahier §6) : index trigramme.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    nom        VARCHAR(120) NOT NULL,
    parent_id  BIGINT REFERENCES categories (id),
    actif      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uk_categories_nom ON categories (lower(nom));

CREATE TABLE produits (
    id                    BIGSERIAL PRIMARY KEY,
    reference             VARCHAR(60)  NOT NULL,
    nom                   VARCHAR(200) NOT NULL,
    nom_fournisseur       VARCHAR(255),
    reference_fournisseur VARCHAR(120),
    categorie_id          BIGINT REFERENCES categories (id),
    unite                 VARCHAR(20)  NOT NULL DEFAULT 'pièce',
    poids_kg              NUMERIC(12, 3),
    volume_m3             NUMERIC(12, 6),
    code_sh               VARCHAR(20),
    suivi_serie           BOOLEAN      NOT NULL DEFAULT FALSE,
    description           TEXT,
    -- RG-01 : un produit avec mouvements ne se supprime pas, il s'archive.
    archive               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT ck_produits_poids CHECK (poids_kg IS NULL OR poids_kg >= 0),
    CONSTRAINT ck_produits_volume CHECK (volume_m3 IS NULL OR volume_m3 >= 0)
);
CREATE UNIQUE INDEX uk_produits_reference ON produits (lower(reference));
CREATE INDEX idx_produits_nom_trgm ON produits USING gin (lower(nom) gin_trgm_ops);
CREATE INDEX idx_produits_categorie ON produits (categorie_id);

CREATE TABLE variantes (
    id             BIGSERIAL PRIMARY KEY,
    produit_id     BIGINT        NOT NULL REFERENCES produits (id),
    sku            VARCHAR(60)   NOT NULL,
    -- Libellé libre des attributs : « Couleur : noir · Taille : M ».
    attributs      VARCHAR(255),
    code_barres    VARCHAR(64),
    prix_detail    BIGINT,
    prix_gros      BIGINT,
    prix_revendeur BIGINT,
    -- Quantité à partir de laquelle le prix de gros s'applique.
    seuil_gros     NUMERIC(14, 3),
    -- Seuil d'alerte de réapprovisionnement.
    seuil_mini     NUMERIC(14, 3) NOT NULL DEFAULT 0,
    archive        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT ck_variantes_prix CHECK (
        (prix_detail IS NULL OR prix_detail >= 0)
        AND (prix_gros IS NULL OR prix_gros >= 0)
        AND (prix_revendeur IS NULL OR prix_revendeur >= 0)),
    CONSTRAINT ck_variantes_seuils CHECK (seuil_mini >= 0 AND (seuil_gros IS NULL OR seuil_gros > 0))
);
CREATE UNIQUE INDEX uk_variantes_sku ON variantes (lower(sku));
CREATE UNIQUE INDEX uk_variantes_code_barres ON variantes (code_barres) WHERE code_barres IS NOT NULL;
CREATE INDEX idx_variantes_produit ON variantes (produit_id);
CREATE INDEX idx_variantes_sku_trgm ON variantes USING gin (lower(sku) gin_trgm_ops);

CREATE TABLE depots (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    nom        VARCHAR(120) NOT NULL,
    adresse    VARCHAR(255),
    actif      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uk_depots_code ON depots (lower(code));

-- Emplacements optionnels dans un dépôt (rayon, étagère).
CREATE TABLE emplacements (
    id         BIGSERIAL PRIMARY KEY,
    depot_id   BIGINT       NOT NULL REFERENCES depots (id),
    code       VARCHAR(30)  NOT NULL,
    libelle    VARCHAR(120),
    actif      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uk_emplacements_code ON emplacements (depot_id, lower(code));

-- ─── Stock : une ligne par (variante, dépôt) ────────────────────────────────
-- Les quatre quantités du cahier §3.3. Disponible = physique − réservée.
-- Ces colonnes ne sont écrites QUE par le service de stock, à partir des
-- mouvements : jamais par une saisie à la main (cahier §4).
CREATE TABLE stocks (
    id                 BIGSERIAL PRIMARY KEY,
    variante_id        BIGINT         NOT NULL REFERENCES variantes (id),
    depot_id           BIGINT         NOT NULL REFERENCES depots (id),
    emplacement_id     BIGINT REFERENCES emplacements (id),
    qte_commandee      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qte_transit        NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qte_physique       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    qte_reservee       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    cump               NUMERIC(18, 4) NOT NULL DEFAULT 0,
    derniere_entree_at TIMESTAMPTZ,
    derniere_sortie_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    CONSTRAINT uk_stocks UNIQUE (variante_id, depot_id),
    -- RG-06 : le stock physique ne peut jamais devenir négatif (filet de
    -- sécurité en base, en plus du contrôle du service).
    CONSTRAINT ck_stocks_physique CHECK (qte_physique >= 0),
    CONSTRAINT ck_stocks_autres CHECK (qte_commandee >= 0 AND qte_transit >= 0 AND qte_reservee >= 0 AND cump >= 0)
);
CREATE INDEX idx_stocks_depot ON stocks (depot_id);

-- ─── Mouvements : journal IMMUABLE ──────────────────────────────────────────
CREATE TABLE mouvements_stock (
    id               BIGSERIAL PRIMARY KEY,
    numero           VARCHAR(30)    NOT NULL,
    type             VARCHAR(30)    NOT NULL,
    -- +1 entrée, −1 sortie.
    sens             SMALLINT       NOT NULL,
    variante_id      BIGINT         NOT NULL REFERENCES variantes (id),
    depot_id         BIGINT         NOT NULL REFERENCES depots (id),
    quantite         NUMERIC(14, 3) NOT NULL,
    cout_unitaire    NUMERIC(18, 4) NOT NULL,
    valeur           BIGINT         NOT NULL,
    stock_apres      NUMERIC(14, 3) NOT NULL,
    cump_apres       NUMERIC(18, 4) NOT NULL,
    motif            VARCHAR(500),
    -- Document source (cahier §3.3) : TRANSFERT, INVENTAIRE, IMPORT_INITIAL…
    document_type    VARCHAR(30),
    document_id      BIGINT,
    -- RG-05 : une erreur se corrige par un mouvement inverse qui pointe ici.
    contre_passe_id  BIGINT REFERENCES mouvements_stock (id),
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    created_by       VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mouvements_sens CHECK (sens IN (-1, 1)),
    CONSTRAINT ck_mouvements_quantite CHECK (quantite > 0),
    CONSTRAINT ck_mouvements_type CHECK (type IN (
        'STOCK_INITIAL', 'ENTREE', 'SORTIE', 'TRANSFERT_SORTIE', 'TRANSFERT_ENTREE',
        'AJUSTEMENT_POSITIF', 'AJUSTEMENT_NEGATIF', 'CASSE', 'RETOUR',
        'INVENTAIRE_POSITIF', 'INVENTAIRE_NEGATIF', 'ANNULATION'))
);
CREATE INDEX idx_mouvements_variante ON mouvements_stock (variante_id, created_at DESC);
CREATE INDEX idx_mouvements_depot ON mouvements_stock (depot_id, created_at DESC);
CREATE INDEX idx_mouvements_numero ON mouvements_stock (numero);
CREATE INDEX idx_mouvements_date ON mouvements_stock (created_at DESC);
-- Un mouvement ne se contre-passe qu'UNE fois.
CREATE UNIQUE INDEX uk_mouvements_contre_passe ON mouvements_stock (contre_passe_id) WHERE contre_passe_id IS NOT NULL;

-- RG-05 : aucun mouvement ne peut être modifié ou supprimé — imposé par la
-- base elle-même, quel que soit le code (ou l'outil SQL) qui s'y essaie.
CREATE FUNCTION interdire_modification_mouvement() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'RG-05 : un mouvement de stock ne peut être ni modifié ni supprimé (corriger par un mouvement inverse).'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mouvements_immuables
    BEFORE UPDATE OR DELETE ON mouvements_stock
    FOR EACH ROW EXECUTE FUNCTION interdire_modification_mouvement();

-- ─── Numéros de série / IMEI (activable par produit) ────────────────────────
CREATE TABLE numeros_serie (
    id          BIGSERIAL PRIMARY KEY,
    variante_id BIGINT      NOT NULL REFERENCES variantes (id),
    numero      VARCHAR(80) NOT NULL,
    depot_id    BIGINT REFERENCES depots (id),
    statut      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uk_numeros_serie UNIQUE (variante_id, numero),
    CONSTRAINT ck_numeros_serie_statut CHECK (statut IN ('EN_STOCK', 'SORTI', 'CASSE')),
    CONSTRAINT ck_numeros_serie_depot CHECK ((statut = 'EN_STOCK') = (depot_id IS NOT NULL))
);
CREATE INDEX idx_numeros_serie_numero ON numeros_serie (numero);

CREATE TABLE mouvement_numeros_serie (
    mouvement_id     BIGINT NOT NULL REFERENCES mouvements_stock (id),
    numero_serie_id  BIGINT NOT NULL REFERENCES numeros_serie (id),
    PRIMARY KEY (mouvement_id, numero_serie_id)
);

-- ─── Inventaires (complet ou tournant) ──────────────────────────────────────
CREATE TABLE inventaires (
    id          BIGSERIAL PRIMARY KEY,
    numero      VARCHAR(30)  NOT NULL,
    depot_id    BIGINT       NOT NULL REFERENCES depots (id),
    type        VARCHAR(20)  NOT NULL,
    statut      VARCHAR(20)  NOT NULL,
    commentaire VARCHAR(500),
    ouvert_par  BIGINT       NOT NULL REFERENCES users (id),
    soumis_par  BIGINT REFERENCES users (id),
    soumis_at   TIMESTAMPTZ,
    valide_par  BIGINT REFERENCES users (id),
    valide_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT uk_inventaires_numero UNIQUE (numero),
    CONSTRAINT ck_inventaires_type CHECK (type IN ('COMPLET', 'TOURNANT')),
    CONSTRAINT ck_inventaires_statut CHECK (statut IN ('EN_COURS', 'A_VALIDER', 'VALIDE', 'ANNULE'))
);
-- Un seul inventaire ouvert à la fois par dépôt.
CREATE UNIQUE INDEX uk_inventaires_ouvert ON inventaires (depot_id) WHERE statut IN ('EN_COURS', 'A_VALIDER');

CREATE TABLE inventaire_lignes (
    id             BIGSERIAL PRIMARY KEY,
    inventaire_id  BIGINT         NOT NULL REFERENCES inventaires (id) ON DELETE CASCADE,
    variante_id    BIGINT         NOT NULL REFERENCES variantes (id),
    -- Photographie du stock physique à l'ouverture : l'écart se calcule
    -- contre elle, et s'applique en DELTA à la validation (les ventes faites
    -- pendant le comptage ne faussent donc pas l'ajustement).
    qte_theorique  NUMERIC(14, 3) NOT NULL,
    qte_comptee    NUMERIC(14, 3),
    compte_par     BIGINT REFERENCES users (id),
    compte_at      TIMESTAMPTZ,
    CONSTRAINT uk_inventaire_lignes UNIQUE (inventaire_id, variante_id),
    CONSTRAINT ck_inventaire_lignes_qte CHECK (qte_theorique >= 0 AND (qte_comptee IS NULL OR qte_comptee >= 0))
);
