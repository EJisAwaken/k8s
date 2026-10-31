package org.esoalahy.stock.modules.inventaire.dto;

import java.time.Instant;
import java.util.List;

public record InventaireDto(
        Long id,
        String numero,
        Long depotId,
        String depotNom,
        String type,
        String statut,
        String commentaire,
        int nbLignes,
        int nbComptees,
        int nbEcarts,
        Long valeurEcartPositif,
        Long valeurEcartNegatif,
        Instant createdAt,
        String createdBy,
        Instant valideAt,
        /* Rempli sur la fiche détaillée seulement. */
        List<InventaireLigneDto> lignes
) {}
