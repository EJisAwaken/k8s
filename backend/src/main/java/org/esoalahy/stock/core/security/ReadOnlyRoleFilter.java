package org.esoalahy.stock.core.security;

import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.RoleRules;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Comptable / gérant = lecture seule (cahier §2 : « ne peut pas saisir ou
 * modifier des opérations »). Appliqué ICI, globalement, plutôt que sur chaque
 * contrôleur : un endpoint d'écriture ajouté demain sans {@code @PreAuthorize}
 * reste fermé au comptable par construction.
 *
 * <p>Seules exceptions : gérer SA session et SON mot de passe.</p>
 */
@Component
public class ReadOnlyRoleFilter extends OncePerRequestFilter {

    private static final Set<String> LECTURE = Set.of("GET", "HEAD", "OPTIONS");

    private static final Set<String> AUTORISES = Set.of(
            "/api/v1/auth/logout",
            "/api/v1/auth/change-password",
            "/api/v1/auth/sessions",
            "/api/v1/auth/2fa/setup",
            "/api/v1/auth/2fa/enable",
            "/api/v1/auth/2fa/disable");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (RoleRules.currentRole() == Role.COMPTABLE
                && !LECTURE.contains(request.getMethod())
                && !estAutorise(request.getRequestURI())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Accès refusé","status":403,\
                    "detail":"Votre profil est en lecture seule : aucune saisie ni modification n'est possible."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean estAutorise(String uri) {
        return AUTORISES.stream().anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }
}
