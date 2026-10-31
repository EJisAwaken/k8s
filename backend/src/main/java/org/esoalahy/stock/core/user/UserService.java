package org.esoalahy.stock.core.user;

import org.esoalahy.stock.core.auth.RefreshTokenService;
import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.NotFoundException;
import org.esoalahy.stock.core.security.PasswordPolicyService;
import org.esoalahy.stock.core.user.dto.UserCreatedDto;
import org.esoalahy.stock.core.user.dto.UserDto;
import org.esoalahy.stock.core.user.dto.UserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Gestion des comptes (réservée à l'administrateur) et changement de son
 * propre mot de passe.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<UserDto> search(Role role, Boolean actif, String q) {
        String like = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return userRepository.search(role, actif, like).stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.from(require(id));
    }

    @Transactional
    public UserCreatedDto create(UserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Un compte existe déjà avec l'e-mail « " + email + " ».");
        }
        String temporaire = passwordPolicyService.genererMotDePasseTemporaire();
        User user = User.builder()
                .email(email)
                .fullName(req.fullName().trim())
                .phone(blankToNull(req.phone()))
                .role(req.role())
                .active(req.active() == null || req.active())
                .passwordHash(passwordEncoder.encode(temporaire))
                .passwordChangeRequired(true)
                .passwordChangedAt(Instant.now())
                .build();
        return new UserCreatedDto(UserDto.from(userRepository.save(user)), temporaire);
    }

    @Transactional
    public UserDto update(Long id, UserRequest req, Long currentUserId) {
        User user = require(id);
        String email = req.email().trim().toLowerCase();
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("Un compte existe déjà avec l'e-mail « " + email + " ».");
        }
        boolean active = req.active() == null ? user.isActive() : req.active();
        protegerDernierAdmin(user, req.role(), active);
        if (id.equals(currentUserId) && (!active || req.role() != user.getRole())) {
            throw new ConflictException("Vous ne pouvez ni désactiver votre propre compte ni changer votre rôle.");
        }
        user.setEmail(email);
        user.setFullName(req.fullName().trim());
        user.setPhone(blankToNull(req.phone()));
        user.setRole(req.role());
        if (user.isActive() && !active) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        user.setActive(active);
        return UserDto.from(user);
    }

    /** Réinitialisation par l'administrateur : mot de passe temporaire + sessions coupées. */
    @Transactional
    public UserCreatedDto resetPassword(Long id) {
        User user = require(id);
        String temporaire = passwordPolicyService.genererMotDePasseTemporaire();
        user.setPasswordHash(passwordEncoder.encode(temporaire));
        user.setPasswordChangeRequired(true);
        user.setPasswordChangedAt(Instant.now());
        refreshTokenService.revokeAllForUser(user.getId());
        return new UserCreatedDto(UserDto.from(user), temporaire);
    }

    /** Désactive la 2FA d'un compte qui a perdu son téléphone (geste administrateur). */
    @Transactional
    public UserDto resetTwoFactor(Long id) {
        User user = require(id);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setRecoveryCodes(null);
        return UserDto.from(user);
    }

    @Transactional
    public void changeMyPassword(Long userId, String currentPassword, String newPassword) {
        User user = require(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        passwordPolicyService.validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setPasswordChangedAt(Instant.now());
    }

    private void protegerDernierAdmin(User user, Role nouveauRole, boolean actif) {
        boolean perdAdmin = user.getRole() == Role.ADMIN && user.isActive()
                && (nouveauRole != Role.ADMIN || !actif);
        if (perdAdmin && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Il doit rester au moins un administrateur actif.");
        }
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
