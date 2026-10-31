package org.esoalahy.stock.modules.stock.dto;

public record TypeMouvementDto(String code, String label, int sens, boolean saisieDirecte, boolean motifObligatoire) {}
