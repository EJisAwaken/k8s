package org.esoalahy.stock.core.security;

import org.esoalahy.stock.core.user.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * émission et validation des access tokens (HS256, 15 min).
 * Claims : {@code userId}, {@code role},
 * {@code jti} (blacklist). Secret obligatoire (≥ 32 octets, sans défaut) avec
 * rotation à double clé.
 */
@Component
public class JwtUtils {

    private static final int MIN_HS256_BYTES = 32;

    private final SecretKey signingKey;
    /** ancienne clé encore acceptée en VALIDATION pendant la fenêtre de rotation (null = pas de rotation en cours). */
    private final SecretKey previousKey;
    private final String issuer;
    private final long expirationSeconds;

    public JwtUtils(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.secret-previous:}") String previousSecret,
            @Value("${app.jwt.issuer:STOCK-IMPORT}") String issuer,
            @Value("${app.jwt.expirationSeconds:900}") long expirationSeconds
    ) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_HS256_BYTES) {
            throw new IllegalStateException(
                    "JWT secret trop court. Fournir APP_JWT_SECRET avec au moins " + MIN_HS256_BYTES + " octets.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);

        // rotation sans déconnexion — APP_JWT_SECRET_PREVIOUS = l'ancien
        // secret, accepté en validation seulement (jamais en signature) le temps
        // que les access tokens en circulation expirent (15 min). À retirer ensuite.
        if (previousSecret != null && !previousSecret.isBlank()) {
            byte[] previousBytes = previousSecret.getBytes(StandardCharsets.UTF_8);
            if (previousBytes.length < MIN_HS256_BYTES) {
                throw new IllegalStateException(
                        "APP_JWT_SECRET_PREVIOUS trop court (min " + MIN_HS256_BYTES + " octets).");
            }
            if (previousSecret.equals(secret)) {
                throw new IllegalStateException(
                        "APP_JWT_SECRET_PREVIOUS identique à APP_JWT_SECRET : rotation sans effet — "
                        + "retirer la variable ou fournir le nouvel APP_JWT_SECRET.");
            }
            this.previousKey = Keys.hmacShaKeyFor(previousBytes);
        } else {
            this.previousKey = null;
        }
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public String generateAccessToken(UserDetails userDetails) {
        String email = userDetails.getUsername();
        Role role = extractSingleRole(userDetails);
        Long userId = userDetails instanceof UserPrincipal up ? up.getUserId() : null;

        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .issuer(issuer)
                .id(UUID.randomUUID().toString())   // jti : identifiant unique (blacklist)
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .claim("role", role.name());

        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parseClaims(token).getPayload().getSubject();
    }

    public Role extractRole(String token) {
        Object raw = parseClaims(token).getPayload().get("role");
        if (raw == null) throw new IllegalArgumentException("Token sans claim 'role'");
        return Role.valueOf(raw.toString());
    }

    public Long extractUserId(String token) {
        Object raw = parseClaims(token).getPayload().get("userId");
        if (raw == null) return null;
        return raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
    }


    /** Identifiant unique du token (jti) — blacklist. */
    public String extractJti(String token) {
        return parseClaims(token).getPayload().getId();
    }

    /** Date d'expiration du token — sert de TTL à l'entrée de blacklist. */
    public Instant extractExpiration(String token) {
        Date exp = parseClaims(token).getPayload().getExpiration();
        return exp == null ? null : exp.toInstant();
    }

    private Jws<Claims> parseClaims(String token) {
        try {
            return parseWith(signingKey, token);
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            // pendant la fenêtre de rotation, un token signé par
            // l'ancien secret reste accepté. Toute autre cause (expiration,
            // issuer, forme) n'est PAS retentée — seule la signature change.
            if (previousKey == null) throw ex;
            return parseWith(previousKey, token);
        }
    }

    private Jws<Claims> parseWith(SecretKey key, String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    private static Role extractSingleRole(UserDetails userDetails) {
        String authority = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_COMPTABLE");
        return Role.valueOf(authority.replaceFirst("^ROLE_", ""));
    }
}
