package org.esoalahy.stock.core.user;

/**
 * Rôles du cahier des charges (§2). Les droits fins sont portés par les
 * {@code @PreAuthorize} des contrôleurs et par {@link RoleRules}.
 */
public enum Role {
    /** Tout, dont utilisateurs, paramètres, taux de change. */
    ADMIN("Administrateur"),
    /** Fournisseurs, bons de commande, expéditions, frais d'import. Ne modifie pas les prix de vente. */
    ACHATS("Responsable achats"),
    /** Réceptions, transferts, préparation, inventaires. Ne voit ni coûts d'achat ni marges. */
    MAGASINIER("Magasinier"),
    /** Clients, devis, commandes, encaissements. Ne modifie pas le stock directement. */
    COMMERCIAL("Commercial"),
    /** Lecture seule : rapports, marges, créances, exports. */
    COMPTABLE("Comptable / gérant");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
