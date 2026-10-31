package org.esoalahy.stock.core.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * journal des connexions (succès/échecs), asynchrone pour ne jamais
 * ralentir le login.
 *
 * <p>Les alertes (nouvel appareil, rafale d'échecs) sont journalisées en WARN
 * pour l'instant — l'envoi SMS/email arrivera avec le module (le SMS est
 * LE canal à Madagascar, le mail ne touche presque personne).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionLogService {

    private final ConnectionLogRepository connectionLogRepository;

    /** alertes de connexion (nouvel appareil, rafale d'échecs). */
    @Value("${app.security.login-alerts.enabled:true}")
    private boolean loginAlertsEnabled;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String email, Long userId, String ip, String userAgent) {
        try {
            // à évaluer AVANT d'insérer la ligne courante (sinon l'IP
            // « inconnue » vient d'être enregistrée et n'alerte jamais).
            boolean newDevice = isNewDevice(userId, ip, truncate(userAgent, 500));

            connectionLogRepository.save(ConnectionLog.builder()
                    .email(email)
                    .userId(userId)
                    .ipAddress(ip)
                    .userAgent(truncate(userAgent, 500))
                    .success(true)
                    .build());
            log.info("LOGIN_SUCCESS email={} ip={}", email, ip);

            if (newDevice) {
                log.warn("connexion depuis un nouvel appareil — email={} ip={}", email, ip);
            }
        } catch (Exception e) {
            log.warn("Impossible d'enregistrer le log de connexion réussie : {}", e.getMessage());
        }
    }

    /**
     * nouvel appareil = IP <em>et</em> navigateur jamais vus sur une
     * connexion réussie de ce compte (l'un OU l'autre seul est trop bruyant :
     * les IP mobiles tournent, un navigateur se met à jour). Jamais d'alerte à
     * la toute première connexion du compte.
     */
    private boolean isNewDevice(Long userId, String ip, String userAgent) {
        if (!loginAlertsEnabled || userId == null || ip == null || userAgent == null) return false;
        if (!connectionLogRepository.existsByUserIdAndSuccessTrue(userId)) return false;
        return !connectionLogRepository.existsByUserIdAndSuccessTrueAndIpAddress(userId, ip)
                && !connectionLogRepository.existsByUserIdAndSuccessTrueAndUserAgent(userId, userAgent);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String email, String ip, String userAgent, String reason) {
        try {
            connectionLogRepository.save(ConnectionLog.builder()
                    .email(email)
                    .userId(null)
                    .ipAddress(ip)
                    .userAgent(truncate(userAgent, 500))
                    .success(false)
                    .failureReason(truncate(reason, 200))
                    .build());
            log.warn("LOGIN_FAILURE email={} ip={} reason={}", email, ip, reason);
        } catch (Exception e) {
            log.warn("Impossible d'enregistrer le log d'échec de connexion : {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
