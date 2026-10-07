package com.interviewprep.link;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Random base62 codes: URL-safe without encoding and unguessable (not sequential). 62^7 ≈ 3.5 × 10^12 possible codes, so
 * collisions are rare; the database's unique constraint is the real guarantee and the service retries on a collision.
 */
@Component
public class ShortCodeGenerator {

    static final int CODE_LENGTH = 7;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
