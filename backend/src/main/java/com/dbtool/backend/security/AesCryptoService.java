package com.dbtool.backend.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM based encryption for target-database connection passwords.
 * <p>
 * The secret key is injected via the {@code APP_AES_KEY} environment variable
 * (mapped to {@code app.security.aes-key}). It is NEVER hardcoded. The key must
 * be a Base64-encoded 16 / 24 / 32 byte value (AES-128/192/256).
 */
@Component
public class AesCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;      // recommended IV size for GCM
    private static final int GCM_TAG_LENGTH = 128;    // auth tag bits
    private static final String PREFIX = "enc:v1:";   // marks encrypted payloads

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    @Value("${app.security.aes-key:}")
    private String base64Key;

    @PostConstruct
    void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "AES key is not configured. Set the APP_AES_KEY environment variable " +
                    "with a Base64-encoded 16/24/32-byte key.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_AES_KEY must be valid Base64.", e);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "APP_AES_KEY must decode to 16, 24 or 32 bytes (got " + keyBytes.length + ").");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypts plaintext; returns a self-describing Base64 payload. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // prepend IV so we can decrypt later
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    /** Decrypts a payload produced by {@link #encrypt(String)}. */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        if (!encrypted.startsWith(PREFIX)) {
            // treat as already-plaintext (defensive; should not normally happen)
            return encrypted;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
