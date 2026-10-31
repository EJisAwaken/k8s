package org.esoalahy.stock.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param code code TOTP à 6 chiffres (ou code de secours), exigé seulement
 *             quand le compte a activé la double authentification.
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        String code
) {}
