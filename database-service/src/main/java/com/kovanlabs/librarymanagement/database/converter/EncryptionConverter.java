package com.kovanlabs.librarymanagement.database.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class EncryptionConverter {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKeySpec keySpec;

    public EncryptionConverter(
            @Value("${encryption.secret-key:${ENCRYPTION_SECRET_KEY}}") String secretKey
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Encryption secret key is not configured"
            );
        }

        byte[] keyBytes = new byte[32];
        byte[] rawBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, keyBytes.length));
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        try {
            log.info("[ENCRYPTION] Encrypting attribute with AES/GCM/NoPadding (Plaintext length: {} chars)", attribute.length());
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] cipherTextWithIv = new byte[IV_LENGTH_BYTE + cipherText.length];
            System.arraycopy(iv, 0, cipherTextWithIv, 0, IV_LENGTH_BYTE);
            System.arraycopy(cipherText, 0, cipherTextWithIv, IV_LENGTH_BYTE, cipherText.length);

            String encryptedBase64 = Base64.getEncoder().encodeToString(cipherTextWithIv);
            log.info("[ENCRYPTION] Successfully encrypted attribute (Ciphertext length: {} chars)", encryptedBase64.length());
            return encryptedBase64;
        } catch (Exception e) {
            log.error("Error encrypting attribute using EncryptionConverter", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        try {
            log.info("[DECRYPTION] Decrypting attribute from database with AES/GCM/NoPadding (Ciphertext length: {} chars)", dbData.length());
            byte[] cipherTextWithIv = Base64.getDecoder().decode(dbData);

            if (cipherTextWithIv.length < IV_LENGTH_BYTE) {
                log.warn("[DECRYPTION] Ciphertext shorter than IV length, returning raw value");
                return dbData;
            }

            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(cipherTextWithIv, 0, iv, 0, IV_LENGTH_BYTE);

            byte[] cipherText = new byte[cipherTextWithIv.length - IV_LENGTH_BYTE];
            System.arraycopy(cipherTextWithIv, IV_LENGTH_BYTE, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            String decryptedString = new String(plainText, StandardCharsets.UTF_8);
            log.info("[DECRYPTION] Successfully decrypted attribute (Plaintext length: {} chars)", decryptedString.length());
            return decryptedString;
        } catch (Exception e) {
            log.warn("Could not decrypt data with EncryptionConverter, returning raw value", e);
            return dbData;
        }
    }
}
