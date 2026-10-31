package org.esoalahy.stock.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * politique de mot de passe configurable.
 *
 * <p>Centralise les règles de robustesse (longueur minimale, présence de
 * majuscules / minuscules / chiffres / caractères spéciaux) et l'expiration
 * périodique. Appelée à chaque point de définition d'un mot de passe :
 * création d'utilisateur, changement, réinitialisation.
 *
 * <p>Les paramètres sont pilotés par les propriétés {@code app.security.password.*}
 * pour permettre un durcissement sans recompilation.
 */
@Service
public class PasswordPolicyService {

    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{};:,.<>?/|\\~`\"'";

    @Value("${app.security.password.min-length:8}")
    private int minLength;

    @Value("${app.security.password.require-uppercase:true}")
    private boolean requireUppercase;

    @Value("${app.security.password.require-lowercase:true}")
    private boolean requireLowercase;

    @Value("${app.security.password.require-digit:true}")
    private boolean requireDigit;

    @Value("${app.security.password.require-special:false}")
    private boolean requireSpecial;

    /** Durée de validité en jours ; {@code <= 0} désactive l'expiration. */
    @Value("${app.security.password.expiry-days:0}")
    private long expiryDays;

    /**
     * Valide un mot de passe en clair contre la politique en vigueur.
     *
     * @throws IllegalArgumentException si une règle n'est pas respectée
     *         (mappé en 400 par {@code GlobalExceptionHandler}).
     */
    public void validate(String rawPassword) {
        List<String> violations = new ArrayList<>();

        if (rawPassword == null || rawPassword.length() < minLength) {
            violations.add(minLength + " caractères minimum");
        }
        String pwd = rawPassword == null ? "" : rawPassword;
        if (requireUppercase && pwd.chars().noneMatch(Character::isUpperCase)) {
            violations.add("une majuscule");
        }
        if (requireLowercase && pwd.chars().noneMatch(Character::isLowerCase)) {
            violations.add("une minuscule");
        }
        if (requireDigit && pwd.chars().noneMatch(Character::isDigit)) {
            violations.add("un chiffre");
        }
        if (requireSpecial && pwd.chars().noneMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0)) {
            violations.add("un caractère spécial");
        }

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Le mot de passe ne respecte pas la politique de sécurité : il faut "
                    + String.join(", ", violations) + ".");
        }
    }

    /**
     * @return {@code true} si le mot de passe est expiré au regard de sa date de
     *         dernier changement. Toujours {@code false} si l'expiration est
     *         désactivée ou si la date est inconnue (comptes hérités).
     */
    public boolean isExpired(Instant passwordChangedAt) {
        if (expiryDays <= 0 || passwordChangedAt == null) {
            return false;
        }
        Instant expiresAt = passwordChangedAt.plus(Duration.ofDays(expiryDays));
        return Instant.now().isAfter(expiresAt);
    }

    public long getExpiryDays() {
        return expiryDays;
    }

    // ── Mot de passe temporaire (écran Utilisateurs / ─────────────────

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Mot de passe temporaire : 12 caractères avec majuscule, minuscule et
     * chiffre garantis (conforme à la politique par défaut), sans caractères
     * ambigus (ni O/0, ni I/l/1) — il se DICTE au comptoir ou au téléphone.
     *
     * <p>Formule EXTRAITE de {@code TenantAdminService.generatePassword}
     *, qui l'appelle désormais — une formule, un seul endroit (même
     * doctrine que {@code PromotionService.remisePourcent}). La valeur est
     * retournée UNE fois dans la réponse du geste qui l'a demandée, jamais
     * journalisée, jamais persistée en clair.</p>
     */
    public String genererMotDePasseTemporaire() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String all = upper + lower + digits;
        StringBuilder sb = new StringBuilder(12);
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        for (int i = 3; i < 12; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        return sb.toString();
    }
}
