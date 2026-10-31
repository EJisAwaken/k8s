package org.esoalahy.stock.modules.catalogue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProduitRequest(
        @NotBlank @Size(max = 60) String reference,
        @NotBlank @Size(max = 200) String nom,
        @Size(max = 255) String nomFournisseur,
        @Size(max = 120) String referenceFournisseur,
        Long categorieId,
        @Size(max = 20) String unite,
        @DecimalMin("0") BigDecimal poidsKg,
        @DecimalMin("0") BigDecimal volumeM3,
        @Size(max = 20) String codeSh,
        Boolean suiviSerie,
        String description,
        /* À la création seulement ; vide → une variante unique de SKU = référence. */
        @Valid List<VarianteRequest> variantes
) {}
