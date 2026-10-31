package org.esoalahy.stock.modules.stock.dto;

import org.esoalahy.stock.modules.stock.TypeMouvement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Mouvement saisi directement : entrée, sortie, ajustement, casse, retour. */
public record MouvementRequest(
        @NotNull TypeMouvement type,
        @NotNull Long depotId,
        @Size(max = 500) String motif,
        @NotEmpty @Valid List<LigneMouvementRequest> lignes
) {}
