package org.esoalahy.stock.modules.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NumeroSerieRepository extends JpaRepository<NumeroSerie, Long> {

    Optional<NumeroSerie> findByVarianteIdAndNumero(Long varianteId, String numero);

    List<NumeroSerie> findByVarianteIdAndDepotIdAndStatutOrderByNumeroAsc(Long varianteId, Long depotId, StatutSerie statut);

    @Query("SELECT n FROM NumeroSerie n JOIN FETCH n.variante v JOIN FETCH v.produit LEFT JOIN FETCH n.depot WHERE n.numero = :numero")
    List<NumeroSerie> findByNumero(@Param("numero") String numero);

    @Modifying
    @Query(value = "INSERT INTO mouvement_numeros_serie (mouvement_id, numero_serie_id) VALUES (:mouvementId, :serieId)",
            nativeQuery = true)
    void lier(@Param("mouvementId") Long mouvementId, @Param("serieId") Long serieId);

    @Query(value = """
            SELECT n.* FROM numeros_serie n
            JOIN mouvement_numeros_serie l ON l.numero_serie_id = n.id
            WHERE l.mouvement_id = :mouvementId ORDER BY n.numero
            """, nativeQuery = true)
    List<NumeroSerie> duMouvement(@Param("mouvementId") Long mouvementId);

    @Query(value = """
            SELECT l.mouvement_id, n.numero FROM mouvement_numeros_serie l
            JOIN numeros_serie n ON n.id = l.numero_serie_id
            WHERE l.mouvement_id IN :ids ORDER BY n.numero
            """, nativeQuery = true)
    List<Object[]> numerosDesMouvements(@Param("ids") Collection<Long> ids);
}
