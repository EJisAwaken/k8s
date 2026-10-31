package org.esoalahy.stock.core.security;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * access token révoqué avant son expiration naturelle, identifié par
 * son {@code jti}. Purgé après {@link #expiresAt} par {@code TokenBlacklistService}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(name = "jti", length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;
}
