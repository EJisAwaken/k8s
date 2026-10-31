package org.esoalahy.stock.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * primitives TOTP (RFC 6238) en JDK pur — 2FA des comptes sensibles
 * (admin, trésorier).
 */
public class TotpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final char[] BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /**
     * Génère un secret Base32 de 32 caractères (160 bits).
     */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Vérifie un code à 6 chiffres par rapport au secret (fenêtre de +- 1 pas de 30s).
     */
    public static boolean verify(String base32Secret, String code) {
        if (code == null || code.length() != 6) return false;
        long timeWindow = System.currentTimeMillis() / 30000;
        try {
            byte[] decodedKey = decodeBase32(base32Secret);
            for (int i = -1; i <= 1; ++i) {
                String hash = generateTotp(decodedKey, timeWindow + i);
                if (hash.equals(code)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * Génère l'URI otpauth pour Google Authenticator.
     */
    public static String getOtpauthUri(String secret, String accountName, String issuer) {
        String i = java.net.URLEncoder.encode(issuer, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String a = java.net.URLEncoder.encode(accountName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", i, a, secret, i);
    }

    private static String generateTotp(byte[] key, long time) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] data = ByteBuffer.allocate(8).putLong(time).array();
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key, HMAC_ALGO));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xf;
        int binary =
                ((hash[offset] & 0x7f) << 24) |
                ((hash[offset + 1] & 0xff) << 16) |
                ((hash[offset + 2] & 0xff) << 8) |
                (hash[offset + 3] & 0xff);

        int otp = binary % 1000000;
        return String.format("%06d", otp);
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = data[0];
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= (data[next++] & 0xff);
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            bitsLeft -= 5;
            int index = (buffer >> bitsLeft) & 0x1f;
            sb.append(BASE32_CHARS[index]);
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String base32) {
        byte[] bytes = new byte[base32.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int next = 0;
        for (char c : base32.toUpperCase().toCharArray()) {
            int val = new String(BASE32_CHARS).indexOf(c);
            if (val < 0) continue;
            buffer <<= 5;
            buffer |= val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bytes[next++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return bytes;
    }
}
