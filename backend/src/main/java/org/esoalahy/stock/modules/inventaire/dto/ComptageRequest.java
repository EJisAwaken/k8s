package org.esoalahy.stock.modules.inventaire.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param ajouter vrai → la quantité s'AJOUTE au comptage existant (scan
 *                successifs, plusieurs rayons) ; faux → elle le remplace.
 */
public record ComptageRequest(@NotNull Long varianteId, @NotNull @DecimalMin("0") BigDecimal quantite, boolean ajouter) {}
