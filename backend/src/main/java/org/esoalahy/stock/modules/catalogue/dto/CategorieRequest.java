package org.esoalahy.stock.modules.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategorieRequest(@NotBlank @Size(max = 120) String nom, Long parentId, Boolean actif) {}
