package org.esoalahy.stock.core.auth;

import org.esoalahy.stock.core.exception.ConflictException;
import org.esoalahy.stock.core.exception.ForbiddenException;
import org.esoalahy.stock.core.qr.QrCodes;
import org.esoalahy.stock.core.security.TotpUtils;
import org.esoalahy.stock.core.user.User;
import org.esoalahy.stock.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 2FA TOTP — obligatoire pour l'administrateur (cahier §6).
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final UserRepository userRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** {@code qrPng} : le QR de l'URI otpauth en data-URL PNG, à scanner avec l'application d'authentification. */
    public record SetupResponse(String secret, String uri, String qrPng, List<String> recoveryCodes) {}

    @Transactional
    public SetupResponse setup(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.isTotpEnabled()) {
            throw new ConflictException("2FA est déjà activé");
        }

        String secret = TotpUtils.generateSecret();
        user.setTotpSecret(secret);

        List<String> recoveryCodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            recoveryCodes.add(String.format("%04d-%04d", RANDOM.nextInt(10000), RANDOM.nextInt(10000)));
        }
        user.setRecoveryCodes(String.join(",", recoveryCodes));

        userRepository.save(user);

        String uri = TotpUtils.getOtpauthUri(secret, user.getEmail(), "Stock Import");
        String qr = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(QrCodes.png(uri, 240));
        return new SetupResponse(secret, uri, qr, recoveryCodes);
    }

    @Transactional
    public void enable(Long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.isTotpEnabled()) {
            throw new ConflictException("2FA est déjà activé");
        }
        if (!TotpUtils.verify(user.getTotpSecret(), code)) {
            throw new ForbiddenException("Code 2FA invalide");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disable(Long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!user.isTotpEnabled()) {
            throw new ConflictException("2FA n'est pas activé");
        }
        if (!TotpUtils.verify(user.getTotpSecret(), code)) {
            throw new ForbiddenException("Code 2FA invalide");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setRecoveryCodes(null);
        userRepository.save(user);
    }

    public boolean verify(User user, String code) {
        if (!user.isTotpEnabled()) return true;

        if (TotpUtils.verify(user.getTotpSecret(), code)) {
            return true;
        }

        // Codes de secours : un code utilisé est consommé.
        if (user.getRecoveryCodes() != null && user.getRecoveryCodes().contains(code)) {
            String updatedCodes = user.getRecoveryCodes().replace(code, "").replace(",,", ",");
            user.setRecoveryCodes(updatedCodes);
            userRepository.save(user);
            return true;
        }
        return false;
    }
}
