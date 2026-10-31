package org.esoalahy.stock.modules.catalogue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VarianteRequest(
        @NotBlank @Size(max = 60) String sku,
        @Size(max = 255) String attributs,
        /* Vide → un EAN-13 interne est généré (préfixe 200, usage magasin). */
        @Size(max = 64) String codeBarres,
        @PositiveOrZero Long prixDetail,
        @PositiveOrZero Long prixGros,
        @PositiveOrZero Long prixRevendeur,
        @DecimalMin(value = "0", inclusive = false) BigDecimal seuilGros,
        @DecimalMin("0") BigDecimal seuilMini
) {}
