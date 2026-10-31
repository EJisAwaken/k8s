package org.esoalahy.stock.core.config;

import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.User;
import org.esoalahy.stock.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Crée le premier administrateur au démarrage si aucun n'existe.
 *
 * <p>En production, le mot de passe d'amorçage DOIT être fourni par
 * {@code APP_BOOTSTRAP_ADMIN_PASSWORD} : le défaut de développement est écrit
 * dans le dépôt, donc public. Le compte créé doit de toute façon changer son
 * mot de passe à la première connexion et activer la 2FA.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BootstrapConfig implements ApplicationRunner {

    private static final String DEFAUT_DEV = "Admin12345";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.bootstrap.admin.enabled:true}")
    private boolean adminEnabled;

    @Value("${app.bootstrap.admin.email:admin@stock.mg}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.fullName:Administrateur}")
    private String adminFullName;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminEnabled || userRepository.countByRoleAndActiveTrue(Role.ADMIN) > 0) {
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.warn("Amorçage : le compte {} existe mais n'est pas un administrateur actif — rien n'est fait.", email);
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .fullName(adminFullName)
                .role(Role.ADMIN)
                .active(true)
                .passwordHash(passwordEncoder.encode(motDePasseAmorcage()))
                .passwordChangeRequired(true)
                .passwordChangedAt(Instant.now())
                .build());
        log.info("Administrateur d'amorçage créé : {}", email);
    }

    private String motDePasseAmorcage() {
        boolean prod = environment.matchesProfiles("prod");
        if (adminPassword == null || adminPassword.isBlank()) {
            if (prod) {
                throw new IllegalStateException(
                        "Amorçage refusé : APP_BOOTSTRAP_ADMIN_PASSWORD n'est pas fourni. En production, "
                        + "le compte d'amorçage ne prend PAS le mot de passe du dépôt.");
            }
            return DEFAUT_DEV;
        }
        if (prod && DEFAUT_DEV.equals(adminPassword)) {
            throw new IllegalStateException(
                    "Amorçage refusé : APP_BOOTSTRAP_ADMIN_PASSWORD reprend le mot de passe écrit dans le dépôt.");
        }
        return adminPassword;
    }
}
