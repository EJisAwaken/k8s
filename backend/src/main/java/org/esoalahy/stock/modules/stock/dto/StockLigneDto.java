package org.esoalahy.stock.modules.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockLigneDto(
        Long varianteId,
        String sku,
        String libelle,
        String unite,
        Long depotId,
        String depotNom,
        String emplacementCode,
        BigDecimal qteCommandee,
        BigDecimal qteTransit,
        BigDecimal qtePhysique,
        BigDecimal qteReservee,
        BigDecimal qteDisponible,
        BigDecimal seuilMini,
        /* null si le profil ne voit pas les coûts (magasinier). */
        BigDecimal cump,
        Long valeur,
        Instant derniereSortieAt
) {}
