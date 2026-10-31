package org.esoalahy.stock.core.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * blacklist des access tokens. Un token révoqué (déconnexion) est
 * neutralisé immédiatement, avant son expiration, en enregistrant son {@code jti}.
 *
 * <p>Stockage PostgreSQL (pas de Redis, appli mono-instance) : la table reste
 * bornée car chaque entrée expire avec le token (15 min) et une purge planifiée
 * élimine les entrées échues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    /** Révoque un access token par son jti, jusqu'à son expiration naturelle. */
    @Transactional
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        // Déjà expiré : inutile de blacklister.
        if (expiresAt.isBefore(Instant.now())) {
            return;
        }
        if (!revokedTokenRepository.existsById(jti)) {
            revokedTokenRepository.save(RevokedToken.builder()
                    .jti(jti)
                    .expiresAt(expiresAt)
                    .revokedAt(Instant.now())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return revokedTokenRepository.existsById(jti);
    }

    /** Purge horaire des tokens expirés (l'expiration naturelle suffit à les invalider). */
    @Scheduled(fixedDelayString = "${app.security.blacklist.purge-interval-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = revokedTokenRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.debug("Blacklist : {} token(s) expiré(s) purgé(s)", removed);
        }
    }
}
