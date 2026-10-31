package org.esoalahy.stock.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndActiveTrue(Role role);

    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR u.role = :role) " +
           "AND (:actif IS NULL OR u.active = :actif) " +
           "AND (:search IS NULL OR LOWER(u.fullName) LIKE :search OR LOWER(u.email) LIKE :search) " +
           "ORDER BY u.fullName ASC")
    List<User> search(@Param("role") Role role,
                      @Param("actif") Boolean actif,
                      @Param("search") String search);
}
