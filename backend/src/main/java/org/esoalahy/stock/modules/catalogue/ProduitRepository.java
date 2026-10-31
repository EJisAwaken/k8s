package org.esoalahy.stock.modules.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProduitRepository extends JpaRepository<Produit, Long> {

    @Query("SELECT p FROM Produit p WHERE LOWER(p.reference) = LOWER(:reference)")
    Optional<Produit> findByReferenceIgnoreCase(@Param("reference") String reference);

    /**
     * Recherche par nom, référence, SKU ou code-barres d'une variante. Les
     * LIKE sur {@code lower(...)} s'appuient sur les index trigrammes (V2).
     */
    @Query(value = """
            SELECT p FROM Produit p LEFT JOIN p.categorie c
            WHERE (:archive IS NULL OR p.archive = :archive)
              AND (:categorieId IS NULL OR c.id = :categorieId)
              AND (:q IS NULL
                   OR LOWER(p.nom) LIKE :q
                   OR LOWER(p.reference) LIKE :q
                   OR LOWER(p.nomFournisseur) LIKE :q
                   OR EXISTS (SELECT 1 FROM Variante v WHERE v.produit = p
                              AND (LOWER(v.sku) LIKE :q OR v.codeBarres = :exact)))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Produit p LEFT JOIN p.categorie c
            WHERE (:archive IS NULL OR p.archive = :archive)
              AND (:categorieId IS NULL OR c.id = :categorieId)
              AND (:q IS NULL
                   OR LOWER(p.nom) LIKE :q
                   OR LOWER(p.reference) LIKE :q
                   OR LOWER(p.nomFournisseur) LIKE :q
                   OR EXISTS (SELECT 1 FROM Variante v WHERE v.produit = p
                              AND (LOWER(v.sku) LIKE :q OR v.codeBarres = :exact)))
            """)
    Page<Produit> search(@Param("q") String q,
                         @Param("exact") String exact,
                         @Param("categorieId") Long categorieId,
                         @Param("archive") Boolean archive,
                         Pageable pageable);

    long countByCategorieId(Long categorieId);
}
