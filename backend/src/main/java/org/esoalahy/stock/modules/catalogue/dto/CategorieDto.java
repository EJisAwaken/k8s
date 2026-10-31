package org.esoalahy.stock.modules.catalogue.dto;

public record CategorieDto(Long id, String nom, Long parentId, String parentNom, boolean actif, long nbProduits) {}
