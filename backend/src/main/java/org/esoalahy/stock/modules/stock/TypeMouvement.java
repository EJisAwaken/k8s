package org.esoalahy.stock.modules.stock;

/**
 * Types de mouvements (cahier §3.3). Le sens est porté par le type, sauf pour
 * {@link #ANNULATION} dont le sens est l'inverse du mouvement annulé.
 */
public enum TypeMouvement {
    STOCK_INITIAL("Stock initial", 1, true),
    ENTREE("Entrée", 1, true),
    SORTIE("Sortie", -1, true),
    TRANSFERT_SORTIE("Transfert (départ)", -1, false),
    TRANSFERT_ENTREE("Transfert (arrivée)", 1, false),
    AJUSTEMENT_POSITIF("Ajustement +", 1, true),
    AJUSTEMENT_NEGATIF("Ajustement −", -1, true),
    CASSE("Casse", -1, true),
    RETOUR("Retour client", 1, true),
    INVENTAIRE_POSITIF("Écart d'inventaire +", 1, false),
    INVENTAIRE_NEGATIF("Écart d'inventaire −", -1, false),
    ANNULATION("Annulation", 0, false);

    private final String label;
    private final int sens;
    /** Saisissable directement depuis l'écran « Nouveau mouvement ». */
    private final boolean saisieDirecte;

    TypeMouvement(String label, int sens, boolean saisieDirecte) {
        this.label = label;
        this.sens = sens;
        this.saisieDirecte = saisieDirecte;
    }

    public String getLabel() {
        return label;
    }

    public int getSens() {
        return sens;
    }

    public boolean isSaisieDirecte() {
        return saisieDirecte;
    }

    /** Une sortie hors vente (ajustement, casse) doit être justifiée. */
    public boolean exigeUnMotif() {
        return this == AJUSTEMENT_POSITIF || this == AJUSTEMENT_NEGATIF || this == CASSE || this == ANNULATION;
    }
}
