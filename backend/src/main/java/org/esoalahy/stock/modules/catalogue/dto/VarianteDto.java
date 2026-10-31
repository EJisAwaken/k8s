package org.esoalahy.stock.modules.catalogue.dto;

import java.math.BigDecimal;

public record VarianteDto(
        Long id,
        Long produitId,
        String sku,
        String attributs,
        String libelle,
        String codeBarres,
        Long prixDetail,
        Long prixGros,
        Long prixRevendeur,
        BigDecimal seuilGros,
        BigDecimal seuilMini,
        boolean archive,
        /* Totaux tous dépôts. */
        BigDecimal qtePhysique,
        BigDecimal qteDisponible
) {}
