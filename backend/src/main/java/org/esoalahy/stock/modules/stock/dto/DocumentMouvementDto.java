package org.esoalahy.stock.modules.stock.dto;

import java.util.List;

/** Résultat d'une opération : le numéro de document et les mouvements produits. */
public record DocumentMouvementDto(String numero, List<MouvementDto> mouvements) {}
