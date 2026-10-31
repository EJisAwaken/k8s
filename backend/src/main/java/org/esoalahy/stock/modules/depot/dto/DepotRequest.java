package org.esoalahy.stock.modules.depot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepotRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "lettres, chiffres, tiret ou souligné uniquement") String code,
        @NotBlank @Size(max = 120) String nom,
        @Size(max = 255) String adresse,
        Boolean actif
) {}
