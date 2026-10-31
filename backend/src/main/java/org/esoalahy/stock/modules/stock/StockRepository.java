package org.esoalahy.stock.modules.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Verrou PESSIMISTE de la ligne (variante, dépôt) : deux mouvements
     * concurrents sur le même article sont sérialisés — c'est ce qui rend
     * RG-06 vrai sous la charge, pas seulement en test unitaire.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.variante.id = :varianteId AND s.depot.id = :depotId")
    Optional<Stock> verrouiller(@Param("varianteId") Long varianteId, @Param("depotId") Long depotId);

    @Query("SELECT s FROM Stock s WHERE s.variante.id = :varianteId AND s.depot.id = :depotId")
    Optional<Stock> trouver(@Param("varianteId") Long varianteId, @Param("depotId") Long depotId);

    /** Totaux tous dépôts : [varianteId, physique, réservée]. */
    @Query("""
            SELECT s.variante.id, SUM(s.qtePhysique), SUM(s.qteReservee)
            FROM Stock s WHERE s.variante.id IN :ids GROUP BY s.variante.id
            """)
    List<Object[]> totauxParVariante(@Param("ids") List<Long> ids);

    @Query("""
            SELECT s FROM Stock s JOIN FETCH s.variante v JOIN FETCH v.produit p JOIN FETCH s.depot d
            LEFT JOIN FETCH s.emplacement
            WHERE (:depotId IS NULL OR d.id = :depotId)
              AND (:varianteId IS NULL OR v.id = :varianteId)
              AND (:q IS NULL OR LOWER(p.nom) LIKE :q OR LOWER(v.sku) LIKE :q OR LOWER(p.reference) LIKE :q
                   OR v.codeBarres = :exact)
              AND (:avecStockSeulement = false OR s.qtePhysique > 0)
            ORDER BY p.nom, v.sku, d.nom
            """)
    List<Stock> etat(@Param("depotId") Long depotId,
                     @Param("varianteId") Long varianteId,
                     @Param("q") String q,
                     @Param("exact") String exact,
                     @Param("avecStockSeulement") boolean avecStockSeulement);

    /** Lignes de stock d'un dépôt (ouverture d'un inventaire complet). */
    @Query("SELECT s FROM Stock s JOIN FETCH s.variante v JOIN FETCH v.produit p WHERE s.depot.id = :depotId AND v.archive = false")
    List<Stock> duDepot(@Param("depotId") Long depotId);

    /** Ruptures et seuils : variantes actives dont le disponible est au seuil ou dessous. */
    @Query("""
            SELECT s FROM Stock s JOIN FETCH s.variante v JOIN FETCH v.produit p JOIN FETCH s.depot d
            WHERE v.archive = false AND p.archive = false AND d.actif = true
              AND (:depotId IS NULL OR d.id = :depotId)
              AND (s.qtePhysique - s.qteReservee) <= v.seuilMini
            ORDER BY (s.qtePhysique - s.qteReservee), p.nom
            """)
    List<Stock> sousSeuil(@Param("depotId") Long depotId);

    /** Stock dormant : du physique, mais aucune sortie depuis la date donnée. */
    @Query("""
            SELECT s FROM Stock s JOIN FETCH s.variante v JOIN FETCH v.produit p JOIN FETCH s.depot d
            WHERE v.archive = false AND s.qtePhysique > 0
              AND (:depotId IS NULL OR d.id = :depotId)
              AND COALESCE(s.derniereSortieAt, s.createdAt) < :depuis
            ORDER BY COALESCE(s.derniereSortieAt, s.createdAt), p.nom
            """)
    List<Stock> dormants(@Param("depotId") Long depotId, @Param("depuis") Instant depuis);
}
