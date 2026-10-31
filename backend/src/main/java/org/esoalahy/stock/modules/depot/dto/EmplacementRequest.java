package org.esoalahy.stock.modules.depot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmplacementRequest(@NotBlank @Size(max = 30) String code, @Size(max = 120) String libelle, Boolean actif) {}
