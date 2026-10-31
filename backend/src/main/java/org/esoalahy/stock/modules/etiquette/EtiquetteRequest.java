package org.esoalahy.stock.modules.etiquette;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EtiquetteRequest(
        @NotNull FormatEtiquette format,
        boolean afficherPrix,
        @NotEmpty @Valid List<Ligne> lignes
) {
    public record Ligne(@NotNull Long varianteId, @Min(1) @Max(500) int copies) {}
}
