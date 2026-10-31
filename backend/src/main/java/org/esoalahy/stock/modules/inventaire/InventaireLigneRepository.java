package org.esoalahy.stock.modules.inventaire;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventaireLigneRepository extends JpaRepository<InventaireLigne, Long> {

    Optional<InventaireLigne> findByInventaireIdAndVarianteId(Long inventaireId, Long varianteId);

    @Query("""
            SELECT l FROM InventaireLigne l JOIN FETCH l.variante v JOIN FETCH v.produit p
            WHERE l.inventaire.id = :inventaireId ORDER BY p.nom, v.sku
            """)
    List<InventaireLigne> lignes(@Param("inventaireId") Long inventaireId);
}
