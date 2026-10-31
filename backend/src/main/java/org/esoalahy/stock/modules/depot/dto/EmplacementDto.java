package org.esoalahy.stock.modules.depot.dto;

import org.esoalahy.stock.modules.depot.Emplacement;

public record EmplacementDto(Long id, Long depotId, String code, String libelle, boolean actif) {
    public static EmplacementDto from(Emplacement e) {
        return new EmplacementDto(e.getId(), e.getDepot().getId(), e.getCode(), e.getLibelle(), e.isActif());
    }
}
