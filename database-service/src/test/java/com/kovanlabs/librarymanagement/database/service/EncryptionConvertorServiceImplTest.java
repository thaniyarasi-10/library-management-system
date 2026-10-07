package com.kovanlabs.librarymanagement.database.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionConvertorServiceImplTest {

    private static final String VALID_KEY = "mySecretKey1234567890123456789012";
    private EncryptionConvertorServiceImpl encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionConvertorServiceImpl(VALID_KEY);
    }

    @Test
    @DisplayName("Constructor should throw IllegalStateException when secretKey is null or blank")
    void constructor_withNullOrBlankKey_shouldThrowException() {
        assertThrows(IllegalStateException.class, () -> new EncryptionConvertorServiceImpl(null));
        assertThrows(IllegalStateException.class, () -> new EncryptionConvertorServiceImpl(""));
        assertThrows(IllegalStateException.class, () -> new EncryptionConvertorServiceImpl("   "));
    }

    @Test
    @DisplayName("Constructor should work with short key by padding to 32 bytes")
    void constructor_withShortKey_shouldSucceed() {
        EncryptionConvertorServiceImpl shortKeyService = new EncryptionConvertorServiceImpl("shortKey");
        String encrypted = shortKeyService.encrypt("Hello");
        assertNotNull(encrypted);
        assertEquals("Hello", shortKeyService.decrypt(encrypted));
    }

    @Test
    @DisplayName("encrypt should return null or empty when input is null or empty")
    void encrypt_withNullOrEmptyInput_shouldReturnInput() {
        assertNull(encryptionService.encrypt(null));
        assertEquals("", encryptionService.encrypt(""));
    }

    @Test
    @DisplayName("decrypt should return null or empty when input is null or empty")
    void decrypt_withNullOrEmptyInput_shouldReturnInput() {
        assertNull(encryptionService.decrypt(null));
        assertEquals("", encryptionService.decrypt(""));
    }

    @Test
    @DisplayName("encrypt and decrypt should restore original plaintext")
    void encryptAndDecrypt_withValidPlaintext_shouldRestoreOriginal() {
        String plaintext = "SensitiveUserData_12345!@#$%^&*()_+";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("decrypt should return raw string when ciphertext length is less than IV length")
    void decrypt_withTooShortCiphertext_shouldReturnRawValue() {
        // Base64 decoding string shorter than 12 bytes
        String shortBase64 = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String result = encryptionService.decrypt(shortBase64);
        assertEquals(shortBase64, result);
    }

    @Test
    @DisplayName("decrypt should return raw string when ciphertext is corrupted or cannot be decrypted")
    void decrypt_withCorruptedCiphertext_shouldReturnRawValue() {
        // 16 bytes invalid AES-GCM data
        String corruptedBase64 = java.util.Base64.getEncoder().encodeToString(new byte[20]);
        String result = encryptionService.decrypt(corruptedBase64);
        assertEquals(corruptedBase64, result);
    }

    @Test
    @DisplayName("decrypt should return raw string when input is not valid base64")
    void decrypt_withInvalidBase64_shouldReturnRawValue() {
        String invalidBase64 = "not-valid-base64!!@@##";
        String result = encryptionService.decrypt(invalidBase64);
        assertEquals(invalidBase64, result);
    }
}
