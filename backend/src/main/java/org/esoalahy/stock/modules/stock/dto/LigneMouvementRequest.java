package org.esoalahy.stock.modules.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record LigneMouvementRequest(
        @NotNull Long varianteId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantite,
        /* Entrées seulement ; vide → coût moyen actuel. Interdit au magasinier. */
        @DecimalMin("0") BigDecimal coutUnitaire,
        /* Obligatoire pour un produit suivi par numéro de série : un numéro par unité. */
        List<String> numerosSerie
) {}
