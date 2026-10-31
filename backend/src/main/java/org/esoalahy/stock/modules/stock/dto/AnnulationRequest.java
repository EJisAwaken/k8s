package org.esoalahy.stock.modules.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnulationRequest(@NotBlank @Size(max = 500) String motif) {}
