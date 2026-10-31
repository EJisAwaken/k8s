package org.esoalahy.stock.modules.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    @Query("SELECT d FROM Depot d WHERE LOWER(d.code) = LOWER(:code)")
    Optional<Depot> findByCodeIgnoreCase(@Param("code") String code);

    List<Depot> findAllByOrderByNomAsc();
}
