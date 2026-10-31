package org.esoalahy.stock.modules.etiquette;

/** Dimensions en millimètres. */
public enum FormatEtiquette {
    /** Planche A4 de 24 étiquettes (3 × 8, 70 × 37 mm) — imprimante bureau. */
    A4_3X8(70f, 37f),
    /** Rouleau 50 × 30 mm — imprimante d'étiquettes thermique. */
    ROULEAU_50X30(50f, 30f);

    final float largeurMm;
    final float hauteurMm;

    FormatEtiquette(float largeurMm, float hauteurMm) {
        this.largeurMm = largeurMm;
        this.hauteurMm = hauteurMm;
    }
}
