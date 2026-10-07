package com.interviewprep;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Signing keys for unit tests, generated at runtime so that no key is ever committed. */
public final class TestSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestSecrets() {}

    /** A random secret of {@code bytes} bytes once encoded as UTF-8 (hex characters are one byte each). */
    public static String randomSecret(int bytes) {
        byte[] random = new byte[(bytes + 1) / 2];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random).substring(0, bytes);
    }

    public static String randomJwtSecret() {
        return randomSecret(64);
    }
}
