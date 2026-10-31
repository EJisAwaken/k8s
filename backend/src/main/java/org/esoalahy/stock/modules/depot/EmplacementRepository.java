package org.esoalahy.stock.modules.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmplacementRepository extends JpaRepository<Emplacement, Long> {

    List<Emplacement> findByDepotIdOrderByCodeAsc(Long depotId);

    @Query("SELECT e FROM Emplacement e WHERE e.depot.id = :depotId AND LOWER(e.code) = LOWER(:code)")
    Optional<Emplacement> findByDepotAndCode(@Param("depotId") Long depotId, @Param("code") String code);
}
