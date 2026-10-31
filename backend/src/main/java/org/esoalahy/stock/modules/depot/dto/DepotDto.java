package org.esoalahy.stock.modules.depot.dto;

import org.esoalahy.stock.modules.depot.Depot;

public record DepotDto(Long id, String code, String nom, String adresse, boolean actif) {
    public static DepotDto from(Depot d) {
        return new DepotDto(d.getId(), d.getCode(), d.getNom(), d.getAdresse(), d.isActif());
    }
}
