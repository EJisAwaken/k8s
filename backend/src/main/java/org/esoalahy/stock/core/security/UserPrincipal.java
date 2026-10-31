package org.esoalahy.stock.core.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class UserPrincipal extends User {

    private final Long userId;

    public UserPrincipal(Long userId, String username, String password,
                         Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, true, authorities);
    }

    public UserPrincipal(Long userId, String username, String password, boolean enabled,
                         Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
    }
}
