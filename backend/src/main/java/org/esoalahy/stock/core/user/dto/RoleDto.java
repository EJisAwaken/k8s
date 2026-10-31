package org.esoalahy.stock.core.user.dto;

import org.esoalahy.stock.core.user.Role;

public record RoleDto(Role code, String label) {
    public static RoleDto from(Role r) {
        return new RoleDto(r, r.getLabel());
    }
}
