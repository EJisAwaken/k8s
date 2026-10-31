package org.esoalahy.stock.core.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Identifiants valides mais code 2FA absent ou faux : le front affiche alors
 * le champ « code ». Rendu en 401 avec {@code code = TOTP_REQUIRED}.
 */
public class TwoFactorRequiredException extends AuthenticationException {

    public static final String CODE = "TOTP_REQUIRED";

    public TwoFactorRequiredException(String message) {
        super(message);
    }
}
