package org.esoalahy.stock.modules.inventaire.dto;

import org.esoalahy.stock.modules.inventaire.TypeInventaire;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OuvertureInventaireRequest(
        @NotNull Long depotId,
        @NotNull TypeInventaire type,
        /* Inventaire tournant : articles à compter (ou une catégorie). */
        List<Long> varianteIds,
        Long categorieId,
        @Size(max = 500) String commentaire
) {}
