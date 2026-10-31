package org.esoalahy.stock.modules.reprise;

import java.util.List;

/**
 * Compte rendu d'un import. Tout ou rien : à la moindre erreur, rien n'est
 * écrit ({@code applique = false}) et chaque ligne fautive est listée.
 */
public record RapportImportDto(
        boolean applique,
        boolean simulation,
        int lignesLues,
        int crees,
        int misAJour,
        String numeroDocument,
        List<ErreurLigne> erreurs
) {
    public record ErreurLigne(int ligne, String message) {}
}
