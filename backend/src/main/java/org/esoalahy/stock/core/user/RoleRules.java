package org.esoalahy.stock.core.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Règles transverses dérivées de la matrice des rôles (cahier §2), lues depuis
 * l'utilisateur courant. Les droits d'accès aux routes restent dans les
 * {@code @PreAuthorize} ; ici ne vivent que les règles de VISIBILITÉ.
 */
public final class RoleRules {

    private RoleRules() {}

    /** Le magasinier ne voit ni les coûts d'achat ni les marges. */
    public static boolean peutVoirLesCouts() {
        Role role = currentRole();
        return role != null && role != Role.MAGASINIER;
    }

    public static Role currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    return Role.valueOf(name.substring(5));
                } catch (IllegalArgumentException ignored) {
                    // autorité étrangère à l'enum : on continue
                }
            }
        }
        return null;
    }
}
