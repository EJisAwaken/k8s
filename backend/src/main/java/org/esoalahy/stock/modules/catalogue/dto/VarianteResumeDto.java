package org.esoalahy.stock.modules.catalogue.dto;

import org.esoalahy.stock.modules.catalogue.Variante;

/** Résultat de recherche rapide (sélecteurs, scan). */
public record VarianteResumeDto(Long id, String sku, String libelle, String codeBarres,
                                String unite, boolean suiviSerie, Long prixDetail) {
    public static VarianteResumeDto from(Variante v) {
        return new VarianteResumeDto(v.getId(), v.getSku(), v.libelle(), v.getCodeBarres(),
                v.getProduit().getUnite(), v.getProduit().isSuiviSerie(), v.getPrixDetail());
    }
}
