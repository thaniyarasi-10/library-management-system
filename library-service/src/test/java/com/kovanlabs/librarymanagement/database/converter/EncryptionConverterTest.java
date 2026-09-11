package com.kovanlabs.librarymanagement.database.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionConverterTest {

    private EncryptionConverter encryptionConverter;

    @BeforeEach
    void setUp() {
        // 32-byte secret key (256-bit AES key)
        encryptionConverter = new EncryptionConverter("01234567890123456789012345678901");
    }

    @Test
    void testEncryptAndDecrypt_shouldWorkCorrectlyAndLog() {
        String rawBase64Signature = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

        // 1. Encrypt (will trigger [ENCRYPTION] logs)
        String encrypted = encryptionConverter.encrypt(rawBase64Signature);

        assertNotNull(encrypted);
        assertNotEquals(rawBase64Signature, encrypted);
        assertTrue(encrypted.length() > 0);

        // 2. Decrypt (will trigger [DECRYPTION] logs)
        String decrypted = encryptionConverter.decrypt(encrypted);

        assertEquals(rawBase64Signature, decrypted);
    }

    @Test
    void testNullAndEmptyHandling() {
        assertNull(encryptionConverter.encrypt(null));
        assertEquals("", encryptionConverter.encrypt(""));
        assertNull(encryptionConverter.decrypt(null));
        assertEquals("", encryptionConverter.decrypt(""));
    }

    @Test
    void testDecryptInvalidData_shouldReturnRawValue() {
        String invalidData = "invalid-base64-text";
        assertEquals(invalidData, encryptionConverter.decrypt(invalidData));

        String shortBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertEquals(shortBase64, encryptionConverter.decrypt(shortBase64));
    }

    @Test
    void testConstructor_invalidSecretKey_shouldThrowException() {
        assertThrows(IllegalStateException.class, () -> new EncryptionConverter(null));
        assertThrows(IllegalStateException.class, () -> new EncryptionConverter(""));
        assertThrows(IllegalStateException.class, () -> new EncryptionConverter("   "));
    }
}

