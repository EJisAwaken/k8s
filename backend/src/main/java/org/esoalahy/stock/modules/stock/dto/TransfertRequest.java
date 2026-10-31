package org.esoalahy.stock.modules.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TransfertRequest(
        @NotNull Long depotSourceId,
        @NotNull Long depotDestinationId,
        @Size(max = 500) String motif,
        @NotEmpty @Valid List<LigneMouvementRequest> lignes
) {}
