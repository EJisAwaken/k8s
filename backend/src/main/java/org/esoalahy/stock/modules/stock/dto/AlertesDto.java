package org.esoalahy.stock.modules.stock.dto;

import java.util.List;

public record AlertesDto(
        List<StockLigneDto> ruptures,
        List<StockLigneDto> sousSeuil,
        List<StockLigneDto> dormants,
        int joursDormance
) {}
