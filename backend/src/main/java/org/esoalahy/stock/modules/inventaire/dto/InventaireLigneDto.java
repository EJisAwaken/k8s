package org.esoalahy.stock.modules.inventaire.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InventaireLigneDto(
        Long id,
        Long varianteId,
        String sku,
        String libelle,
        String codeBarres,
        boolean suiviSerie,
        BigDecimal qteTheorique,
        BigDecimal qteComptee,
        BigDecimal ecart,
        /* Valeur de l'écart au CUMP, null si le profil ne voit pas les coûts. */
        Long valeurEcart,
        Instant compteAt
) {}
