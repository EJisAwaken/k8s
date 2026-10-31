package org.esoalahy.stock.core.auth.dto;

import org.esoalahy.stock.core.user.Role;
import lombok.Builder;

@Builder
public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Long userId,
        String email,
        String fullName,
        Role role,
        /* true si le mot de passe a expiré ou est temporaire → le front impose de le changer. */
        boolean passwordExpired,
        /* true pour un administrateur sans 2FA → le front impose de l'activer (cahier §6). */
        boolean twoFactorSetupRequired
) {}
