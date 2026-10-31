package org.esoalahy.stock.modules.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Coûts et valeurs à {@code null} pour un profil qui ne voit pas les coûts. */
public record MouvementDto(
        Long id,
        String numero,
        String type,
        String typeLabel,
        int sens,
        Long varianteId,
        String sku,
        String libelle,
        Long depotId,
        String depotNom,
        BigDecimal quantite,
        BigDecimal coutUnitaire,
        Long valeur,
        BigDecimal stockApres,
        String motif,
        String documentType,
        Long documentId,
        Long contrePasseId,
        boolean annule,
        List<String> numerosSerie,
        String createdBy,
        Instant createdAt
) {}
