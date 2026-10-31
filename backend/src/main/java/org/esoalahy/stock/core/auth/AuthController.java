package org.esoalahy.stock.core.auth;

import org.esoalahy.stock.core.audit.ConnectionLogService;
import org.esoalahy.stock.core.auth.dto.AuthResponse;
import org.esoalahy.stock.core.auth.dto.ChangePasswordRequest;
import org.esoalahy.stock.core.auth.dto.LoginRequest;
import org.esoalahy.stock.core.auth.dto.SessionResponse;
import org.esoalahy.stock.core.exception.TwoFactorRequiredException;
import org.esoalahy.stock.core.security.CurrentUser;
import org.esoalahy.stock.core.security.JwtUtils;
import org.esoalahy.stock.core.security.LoginAttemptService;
import org.esoalahy.stock.core.security.PasswordPolicyService;
import org.esoalahy.stock.core.security.StockUserDetailsService;
import org.esoalahy.stock.core.security.TokenBlacklistService;
import org.esoalahy.stock.core.security.UserPrincipal;
import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.User;
import org.esoalahy.stock.core.user.UserRepository;
import org.esoalahy.stock.core.user.UserService;
import org.esoalahy.stock.core.user.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Authentification — login (avec 2FA), refresh silencieux, logout, sessions,
 * changement de son mot de passe.
 *
 * <p>Pas de « mot de passe oublié » par e-mail dans le socle : le canal
 * d'envoi n'existe pas encore (lot 4). L'administrateur réinitialise le mot
 * de passe depuis l'écran Utilisateurs ; le compte reçoit un mot de passe
 * temporaire à changer à la connexion suivante.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ConnectionLogService connectionLogService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordPolicyService passwordPolicyService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieService refreshCookieService;
    private final TokenBlacklistService tokenBlacklistService;
    private final TwoFactorService twoFactorService;

    // ── Login / refresh / logout ─────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String email     = request.email().trim().toLowerCase();
        String ip        = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Anti-brute-force : refuse l'authentification si le compte est verrouillé.
        // Non journalisé comme échec pour ne pas prolonger le verrou.
        Optional<Long> lockRemaining = loginAttemptService.lockRemainingSeconds(email);
        if (lockRemaining.isPresent()) {
            long minutes = (lockRemaining.get() + 59) / 60;
            throw new LockedException(
                    "Trop de tentatives échouées. Compte temporairement verrouillé. "
                    + "Réessayez dans " + minutes + " minute" + (minutes > 1 ? "s" : "") + ".");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            connectionLogService.logFailure(email, ip, userAgent, "Identifiants invalides");
            throw new BadCredentialsException("Identifiants invalides");
        } catch (DisabledException ex) {
            // Compte désactivé : même refus NEUTRE que des identifiants faux —
            // sinon la réponse renseignerait quiconque sonde des adresses.
            connectionLogService.logFailure(email, ip, userAgent, "Compte désactivé");
            throw new BadCredentialsException("Identifiants invalides");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        // 2FA : le mot de passe est bon, le second facteur doit l'être aussi.
        if (user.isTotpEnabled()) {
            if (request.code() == null || request.code().isBlank()) {
                throw new TwoFactorRequiredException("Saisissez le code de votre application d'authentification.");
            }
            if (!twoFactorService.verify(user, request.code().trim())) {
                connectionLogService.logFailure(email, ip, userAgent, "Code 2FA invalide");
                throw new TwoFactorRequiredException("Code de vérification invalide.");
            }
        }

        connectionLogService.logSuccess(user.getEmail(), user.getId(), ip, userAgent);

        // Ouverture d'une session + dépôt du refresh token en cookie httpOnly.
        String refreshToken = refreshTokenService.createSession(user, userAgent, ip);
        ResponseCookie cookie = refreshCookieService.build(refreshToken, httpRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(buildAuthResponse(user));
    }

    /**
     * Renouvellement silencieux. Consomme le refresh token du cookie httpOnly,
     * le tourne (rotation) et renvoie un nouvel access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest httpRequest) {
        String rawToken  = refreshCookieService.readToken(httpRequest).orElse(null);
        String ip        = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawToken, userAgent, ip)
                .orElseThrow(() -> new BadCredentialsException("Session expirée ou invalide"));

        User user = rotation.user();
        if (!user.isActive()) {
            throw new BadCredentialsException("Compte désactivé");
        }

        ResponseCookie cookie = refreshCookieService.build(rotation.rawToken(), httpRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(buildAuthResponse(user));
    }

    /**
     * Déconnexion — révoque la session (refresh) courante, blackliste l'access
     * token présenté (invalidation immédiate) et efface le cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        refreshCookieService.readToken(httpRequest).ifPresent(refreshTokenService::revokeByToken);
        blacklistBearerToken(httpRequest);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.clearing(httpRequest).toString())
                .build();
    }

    // ── Moi ───────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public UserDto me(@CurrentUser Long userId) {
        return userService.get(userId);
    }

    /** Change SON mot de passe ; les autres appareils sont déconnectés. */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@CurrentUser Long userId,
                               @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        userService.changeMyPassword(userId, request.currentPassword(), request.newPassword());
        Long currentSession = refreshCookieService.readToken(httpRequest)
                .flatMap(refreshTokenService::findSessionIdByToken)
                .orElse(null);
        refreshTokenService.revokeAllForUserExcept(userId, currentSession);
    }

    // ── Sessions (appareils connectés) ───────────────────────────────────────

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@CurrentUser Long userId, HttpServletRequest httpRequest) {
        Long currentSessionId = refreshCookieService.readToken(httpRequest)
                .flatMap(refreshTokenService::findSessionIdByToken)
                .orElse(null);
        return refreshTokenService.listActive(userId).stream()
                .map(s -> SessionResponse.from(s, s.getId().equals(currentSessionId)))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@CurrentUser Long userId, @PathVariable Long id) {
        refreshTokenService.revokeById(id, userId);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@CurrentUser Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    // ── 2FA TOTP ─────────────────────────────────────────────────────────────

    @PostMapping("/2fa/setup")
    public TwoFactorService.SetupResponse setup2fa(@CurrentUser Long userId) {
        return twoFactorService.setup(userId);
    }

    @PostMapping("/2fa/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable2fa(@CurrentUser Long userId, @RequestParam String code) {
        twoFactorService.enable(userId, code);
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable2fa(@CurrentUser Long userId, @RequestParam String code) {
        twoFactorService.disable(userId, code);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void blacklistBearerToken(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return;
        }
        String token = header.substring(7).trim();
        if (jwtUtils.isTokenValid(token)) {
            tokenBlacklistService.blacklist(jwtUtils.extractJti(token), jwtUtils.extractExpiration(token));
        }
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(jwtUtils.generateAccessToken(StockUserDetailsService.toPrincipal(user)))
                .expiresIn(jwtUtils.getExpirationSeconds())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .passwordExpired(passwordPolicyService.isExpired(user.getPasswordChangedAt())
                        || user.isPasswordChangeRequired())
                .twoFactorSetupRequired(user.getRole() == Role.ADMIN && !user.isTotpEnabled())
                .build();
    }
}
