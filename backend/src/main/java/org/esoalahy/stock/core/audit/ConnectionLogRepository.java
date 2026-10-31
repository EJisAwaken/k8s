package org.esoalahy.stock.core.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ConnectionLogRepository extends JpaRepository<ConnectionLog, Long> {

    Page<ConnectionLog> findByEmailOrderByLoggedAtDesc(String email, Pageable pageable);

    // détection de nouvel appareil — l'utilisateur a-t-il déjà réussi
    // une connexion, et depuis cette IP / ce navigateur ?
    boolean existsByUserIdAndSuccessTrue(Long userId);

    boolean existsByUserIdAndSuccessTrueAndIpAddress(Long userId, String ipAddress);

    boolean existsByUserIdAndSuccessTrueAndUserAgent(Long userId, String userAgent);

    // Anti-brute-force : date du dernier succès pour cet email (null si aucun).
    @Query("SELECT MAX(c.loggedAt) FROM ConnectionLog c " +
           "WHERE LOWER(c.email) = LOWER(:email) AND c.success = true")
    Instant findLastSuccessAt(@Param("email") String email);

    // Anti-brute-force : horodatages des échecs depuis :since, du plus récent au plus ancien.
    @Query("SELECT c.loggedAt FROM ConnectionLog c " +
           "WHERE LOWER(c.email) = LOWER(:email) AND c.success = false AND c.loggedAt >= :since " +
           "ORDER BY c.loggedAt DESC")
    List<Instant> findFailureTimestampsSince(@Param("email") String email,
                                             @Param("since") Instant since);
}
