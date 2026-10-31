package org.esoalahy.stock.modules.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    @Query("SELECT v FROM Variante v JOIN FETCH v.produit WHERE LOWER(v.sku) = LOWER(:sku)")
    Optional<Variante> findBySkuIgnoreCase(@Param("sku") String sku);

    @Query("SELECT v FROM Variante v JOIN FETCH v.produit WHERE v.codeBarres = :code")
    Optional<Variante> findByCodeBarres(@Param("code") String code);

    @Query("SELECT v FROM Variante v JOIN FETCH v.produit WHERE v.id IN :ids")
    List<Variante> findAllWithProduit(@Param("ids") List<Long> ids);

    /** Recherche rapide pour les sélecteurs (saisie de mouvement, inventaire, étiquettes). */
    @Query("""
            SELECT v FROM Variante v JOIN FETCH v.produit p
            WHERE v.archive = false AND p.archive = false
              AND (LOWER(v.sku) LIKE :q OR LOWER(p.nom) LIKE :q OR LOWER(p.reference) LIKE :q
                   OR v.codeBarres = :exact)
            ORDER BY p.nom, v.sku
            """)
    List<Variante> rechercher(@Param("q") String q, @Param("exact") String exact,
                              org.springframework.data.domain.Pageable limite);

    boolean existsByCodeBarres(String codeBarres);

    @Query("SELECT v FROM Variante v JOIN FETCH v.produit p WHERE p.categorie.id = :categorieId AND v.archive = false AND p.archive = false")
    List<Variante> activesDeLaCategorie(@Param("categorieId") Long categorieId);
}
