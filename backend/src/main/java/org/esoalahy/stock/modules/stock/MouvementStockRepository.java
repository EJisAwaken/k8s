package org.esoalahy.stock.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {

    boolean existsByVarianteId(Long varianteId);

    @Query("SELECT COUNT(m) > 0 FROM MouvementStock m WHERE m.variante.produit.id = :produitId")
    boolean existsByProduitId(@Param("produitId") Long produitId);

    boolean existsByContrePasseId(Long contrePasseId);

    List<MouvementStock> findByNumeroOrderByIdAsc(String numero);

    /** Parmi ces mouvements, ceux qui ont déjà été contre-passés. */
    @Query("SELECT m.contrePasseId FROM MouvementStock m WHERE m.contrePasseId IN :ids")
    List<Long> contrePassesParmi(@Param("ids") java.util.Collection<Long> ids);

    @Query(value = """
            SELECT m FROM MouvementStock m JOIN FETCH m.variante v JOIN FETCH v.produit p JOIN FETCH m.depot d
            WHERE (:depotId IS NULL OR d.id = :depotId)
              AND (:varianteId IS NULL OR v.id = :varianteId)
              AND (:type IS NULL OR m.type = :type)
              AND (:numero IS NULL OR m.numero = :numero)
              AND m.createdAt >= :du
              AND m.createdAt < :au
            ORDER BY m.createdAt DESC, m.id DESC
            """,
            countQuery = """
            SELECT COUNT(m) FROM MouvementStock m
            WHERE (:depotId IS NULL OR m.depot.id = :depotId)
              AND (:varianteId IS NULL OR m.variante.id = :varianteId)
              AND (:type IS NULL OR m.type = :type)
              AND (:numero IS NULL OR m.numero = :numero)
              AND m.createdAt >= :du
              AND m.createdAt < :au
            """)
    Page<MouvementStock> journal(@Param("depotId") Long depotId,
                                 @Param("varianteId") Long varianteId,
                                 @Param("type") TypeMouvement type,
                                 @Param("numero") String numero,
                                 @Param("du") Instant du,
                                 @Param("au") Instant au,
                                 Pageable pageable);
}
