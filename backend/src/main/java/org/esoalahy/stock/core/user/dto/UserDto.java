package org.esoalahy.stock.core.user.dto;

import org.esoalahy.stock.core.user.Role;
import org.esoalahy.stock.core.user.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        String roleLabel,
        boolean active,
        boolean totpEnabled,
        boolean passwordChangeRequired,
        Instant createdAt
) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole(), u.getRole().getLabel(), u.isActive(), u.isTotpEnabled(),
                u.isPasswordChangeRequired(), u.getCreatedAt());
    }
}
