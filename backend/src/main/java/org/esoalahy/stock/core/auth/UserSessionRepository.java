package org.esoalahy.stock.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    /** Variante avec chargement de l'utilisateur (refresh : accès aux champs hors transaction). */
    @Query("SELECT s FROM UserSession s JOIN FETCH s.user WHERE s.refreshTokenHash = :hash")
    Optional<UserSession> findByRefreshTokenHashWithUser(@Param("hash") String hash);

    /** Sessions actives (non révoquées, non expirées) d'un utilisateur, plus récentes d'abord. */
    @Query("""
            SELECT s FROM UserSession s
            WHERE s.user.id = :userId
              AND s.revokedAt IS NULL
              AND s.expiresAt > :now
            ORDER BY s.lastUsedAt DESC
            """)
    List<UserSession> findActiveByUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Révoque toutes les sessions actives d'un utilisateur (« déconnecter tous les appareils »). */
    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now
            WHERE s.user.id = :userId AND s.revokedAt IS NULL
            """)
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Révoque toutes les sessions actives d'un utilisateur SAUF une — le
     * changement de son propre mot de passe déconnecte les AUTRES appareils
     * (qui connaissaient peut-être l'ancien), jamais celui depuis lequel on
     * vient de le changer.
     */
    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now
            WHERE s.user.id = :userId AND s.revokedAt IS NULL AND s.id <> :sessionId
            """)
    int revokeAllActiveForUserExcept(@Param("userId") Long userId,
                                     @Param("sessionId") Long sessionId,
                                     @Param("now") Instant now);
}
