package org.esoalahy.stock.core.auth.dto;

import org.esoalahy.stock.core.auth.UserSession;
import lombok.Builder;

import java.time.Instant;

/**
 * vue d'une session active pour l'écran de gestion des appareils.
 * {@code current} indique la session depuis laquelle la requête est émise.
 */
@Builder
public record SessionResponse(
        Long id,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current
) {
    public static SessionResponse from(UserSession s, boolean current) {
        return SessionResponse.builder()
                .id(s.getId())
                .userAgent(s.getUserAgent())
                .ipAddress(s.getIpAddress())
                .createdAt(s.getCreatedAt())
                .lastUsedAt(s.getLastUsedAt())
                .current(current)
                .build();
    }
}
