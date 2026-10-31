package org.esoalahy.stock.core.auth;

import org.esoalahy.stock.core.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * gestion des refresh tokens et des sessions.
 *
 * <p>Le refresh token est une valeur opaque aléatoire (256 bits) transmise au
 * client via un cookie httpOnly ; seule son empreinte SHA-256 est persistée.
 * À chaque rafraîchissement, le token est <b>tourné</b> (rotation) : l'ancien
 * est invalidé, un nouveau est émis sur la même session — ce qui neutralise un
 * refresh token dérobé dès la première réutilisation légitime.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserSessionRepository sessionRepository;

    @Value("${app.jwt.refresh.ttl-seconds:2592000}")
    private long refreshTtlSeconds;

    /** Résultat d'une rotation réussie : l'utilisateur et le nouveau token en clair. */
    public record Rotation(User user, String rawToken) {}

    /**
     * Ouvre une nouvelle session et retourne le refresh token en clair
     * (à poser dans le cookie httpOnly).
     */
    @Transactional
    public String createSession(User user, String userAgent, String ipAddress) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();
        UserSession session = UserSession.builder()
                .user(user)
                .refreshTokenHash(hash(rawToken))
                .userAgent(truncate(userAgent, 500))
                .ipAddress(truncate(ipAddress, 100))
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plus(Duration.ofSeconds(refreshTtlSeconds)))
                .build();
        sessionRepository.save(session);
        return rawToken;
    }

    /**
     * Valide un refresh token et tourne la session associée.
     *
     * @return la rotation (utilisateur + nouveau token) si la session est active,
     *         {@link Optional#empty()} sinon (token inconnu, révoqué ou expiré).
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return sessionRepository.findByRefreshTokenHashWithUser(hash(rawToken))
                .filter(session -> session.isActive(now))
                .map(session -> {
                    String newRawToken = generateRawToken();
                    session.setRefreshTokenHash(hash(newRawToken));
                    session.setLastUsedAt(now);
                    session.setExpiresAt(now.plus(Duration.ofSeconds(refreshTtlSeconds)));
                    if (userAgent != null) session.setUserAgent(truncate(userAgent, 500));
                    if (ipAddress != null) session.setIpAddress(truncate(ipAddress, 100));
                    // L'entité étant managée, la modification est persistée au commit.
                    return new Rotation(session.getUser(), newRawToken);
                });
    }

    /** Révoque la session portant ce refresh token (déconnexion). */
    @Transactional
    public void revokeByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        sessionRepository.findByRefreshTokenHash(hash(rawToken))
                .filter(s -> s.getRevokedAt() == null)
                .ifPresent(s -> s.setRevokedAt(Instant.now()));
    }

    /**
     * Révoque une session précise, uniquement si elle appartient à l'utilisateur.
     * @return true si une session active a été révoquée.
     */
    @Transactional
    public boolean revokeById(Long sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(userId) && s.getRevokedAt() == null)
                .map(s -> { s.setRevokedAt(Instant.now()); return true; })
                .orElse(false);
    }

    /** Révoque toutes les sessions actives de l'utilisateur. @return nombre révoqué. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return sessionRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    /**
     * Révoque toutes les sessions actives de l'utilisateur SAUF celle-ci
     * (changement de SON mot de passe : les autres appareils — qui
     * connaissaient peut-être l'ancien — sont déconnectés, jamais celui depuis
     * lequel on vient de le changer). {@code sessionId} null = tout révoquer.
     */
    @Transactional
    public int revokeAllForUserExcept(Long userId, Long sessionId) {
        if (sessionId == null) {
            return revokeAllForUser(userId);
        }
        return sessionRepository.revokeAllActiveForUserExcept(userId, sessionId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActive(Long userId) {
        return sessionRepository.findActiveByUser(userId, Instant.now());
    }

    /** Identifiant de session correspondant à un refresh token (pour marquer la session courante). */
    @Transactional(readOnly = true)
    public Optional<Long> findSessionIdByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return sessionRepository.findByRefreshTokenHash(hash(rawToken)).map(UserSession::getId);
    }

    // ── Interne ────────────────────────────────────────────────────────────────

    private static String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits d'entropie
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
