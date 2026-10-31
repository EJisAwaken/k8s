package org.esoalahy.stock.core.auth;

import org.esoalahy.stock.core.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * session active d'un utilisateur, matérialisant un appareil connecté.
 *
 * <p>Le refresh token opaque n'est stocké que sous forme d'empreinte SHA-256
 * ({@link #refreshTokenHash}). Une session est valide tant qu'elle n'est ni
 * révoquée ({@link #revokedAt}) ni expirée ({@link #expiresAt}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_sessions_hash", columnNames = "refresh_token_hash"))
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Session utilisable : ni révoquée, ni expirée. */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
