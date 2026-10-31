package org.esoalahy.stock.modules.inventaire;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventaireRepository extends JpaRepository<Inventaire, Long> {

    @Query("""
            SELECT i FROM Inventaire i JOIN FETCH i.depot d
            WHERE (:depotId IS NULL OR d.id = :depotId)
              AND (:statut IS NULL OR i.statut = :statut)
            ORDER BY i.createdAt DESC
            """)
    List<Inventaire> search(@Param("depotId") Long depotId, @Param("statut") StatutInventaire statut);

    boolean existsByDepotIdAndStatutIn(Long depotId, List<StatutInventaire> statuts);
}
