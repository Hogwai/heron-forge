package dev.hogwai.platform.runtime.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the content-derived identity of a generation.
 *
 * <p>The identity of a generation is the full SHA-256 hexadecimal digest
 * (64 characters) of the UTF-8 bytes of its raw, unresolved YAML. Both
 * {@link RegistryService} (sealing on registration) and
 * {@link GenerationActivator} (integrity check on activation) derive the
 * identity through this single helper so the digest definition cannot drift.
 */
final class GenerationDigest {

    private static final String ALGORITHM = "SHA-256";

    private GenerationDigest() {
        // no instances
    }

    /**
     * Computes the SHA-256 hexadecimal digest of the UTF-8 bytes of the content.
     *
     * @param content the content to seal
     * @return the lowercase hexadecimal digest, always 64 characters long
     */
    static String sha256Hex(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("the SHA-256 message digest algorithm is not available", failure);
        }
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
