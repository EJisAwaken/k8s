package org.esoalahy.stock.core.user.dto;

import org.esoalahy.stock.core.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Création ou modification d'un compte par l'administrateur. */
public record UserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 50) String phone,
        @NotNull Role role,
        Boolean active
) {}
