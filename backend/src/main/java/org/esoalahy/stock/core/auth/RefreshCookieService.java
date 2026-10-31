package org.esoalahy.stock.core.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

/**
 * fabrique et lecture du cookie httpOnly portant le refresh token.
 *
 * <p>Paramétrable via {@code app.jwt.refresh.cookie.*} : nom, chemin, drapeau
 * {@code Secure} et politique {@code SameSite} (à passer en {@code None}+Secure
 * pour un déploiement front/back sur domaines distincts).
 */
@Service
public class RefreshCookieService {

    @Value("${app.jwt.refresh.cookie.name:stock_refresh}")
    private String cookieName;

    @Value("${app.jwt.refresh.cookie.path:/api/v1/auth}")
    private String cookiePath;

    @Value("${app.jwt.refresh.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.refresh.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.jwt.refresh.ttl-seconds:2592000}")
    private long refreshTtlSeconds;

    public String getCookieName() {
        return cookieName;
    }

    /** Cookie déposant le refresh token pour la durée de vie configurée. */
    public ResponseCookie build(String rawToken, HttpServletRequest request) {
        return baseBuilder(rawToken, request)
                .maxAge(refreshTtlSeconds)
                .build();
    }

    /** Cookie d'expiration immédiate (déconnexion). */
    public ResponseCookie clearing(HttpServletRequest request) {
        return baseBuilder("", request)
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value, HttpServletRequest request) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure || (request != null && request.isSecure()))
                .sameSite(cookieSameSite)
                .path(cookiePath);
    }

    /** Lit le refresh token depuis les cookies de la requête, s'il est présent. */
    public Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
