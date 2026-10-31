package org.esoalahy.stock.modules.stock.dto;

public record NumeroSerieDto(Long id, String numero, Long varianteId, String sku, String libelle,
                             String statut, Long depotId, String depotNom) {}
