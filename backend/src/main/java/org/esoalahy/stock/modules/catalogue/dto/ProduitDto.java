package org.esoalahy.stock.modules.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProduitDto(
        Long id,
        String reference,
        String nom,
        String nomFournisseur,
        String referenceFournisseur,
        Long categorieId,
        String categorieNom,
        String unite,
        BigDecimal poidsKg,
        BigDecimal volumeM3,
        String codeSh,
        boolean suiviSerie,
        String description,
        boolean archive,
        /* RG-01 : vrai si le produit a déjà des mouvements (suppression impossible). */
        boolean aDesMouvements,
        List<VarianteDto> variantes
) {}
