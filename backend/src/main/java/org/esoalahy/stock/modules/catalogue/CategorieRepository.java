package org.esoalahy.stock.modules.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    @Query("SELECT c FROM Categorie c WHERE LOWER(c.nom) = LOWER(:nom)")
    Optional<Categorie> findByNomIgnoreCase(String nom);

    List<Categorie> findAllByOrderByNomAsc();

    long countByParentId(Long parentId);
}
