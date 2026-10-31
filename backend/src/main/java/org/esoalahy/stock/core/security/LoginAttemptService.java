package org.esoalahy.stock.core.security;

import org.esoalahy.stock.core.audit.ConnectionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * protection anti-brute-force — après {@code maxAttempts} échecs
 * consécutifs de connexion (dans la fenêtre {@code lockMinutes}),
 * l'authentification est temporairement refusée, même avec des identifiants
 * valides.
 *
 * <p>Les tentatives sont lues depuis {@code connection_logs} : on compte les
 * échecs postérieurs au dernier succès et compris dans la fenêtre glissante.
 * Les refus dus au verrouillage ne sont pas journalisés comme échecs, ce qui
 * évite de prolonger indéfiniment le verrou.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final ConnectionLogRepository connectionLogRepository;

    @Value("${app.security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.login.lock-minutes:5}")
    private long lockMinutes;

    /**
     * @return le nombre de secondes de verrouillage restantes, ou {@link Optional#empty()}
     *         si le compte n'est pas verrouillé.
     */
    public Optional<Long> lockRemainingSeconds(String email) {
        if (email == null || email.isBlank()) return Optional.empty();

        Duration lockDuration = Duration.ofMinutes(lockMinutes);
        Instant windowStart = Instant.now().minus(lockDuration);

        // Un succès plus récent que la fenêtre remet le compteur à zéro.
        Instant lastSuccess = connectionLogRepository.findLastSuccessAt(email);
        Instant since = (lastSuccess != null && lastSuccess.isAfter(windowStart)) ? lastSuccess : windowStart;

        List<Instant> failures = connectionLogRepository.findFailureTimestampsSince(email, since);
        if (failures.size() < maxAttempts) return Optional.empty();

        // failures triés du plus récent au plus ancien : get(0) = dernier échec.
        Instant lockUntil = failures.get(0).plus(lockDuration);
        long remaining = Duration.between(Instant.now(), lockUntil).getSeconds();
        return remaining > 0 ? Optional.of(remaining) : Optional.empty();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }
}
