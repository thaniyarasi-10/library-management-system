package com.kovanlabs.librarymanagement.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AadhaarSecurityServiceImplTest {

    private AadhaarSecurityServiceImpl aadhaarSecurityService;

    @BeforeEach
    void setUp() {
        aadhaarSecurityService = new AadhaarSecurityServiceImpl("TestSecretKeyForAadhaarHmac123456789!");
    }

    @Test
    @DisplayName("Constructor should throw when secret key is null or blank")
    void constructor_invalidSecret_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new AadhaarSecurityServiceImpl(null));
        assertThrows(IllegalStateException.class, () -> new AadhaarSecurityServiceImpl("   "));
    }

    @Test
    @DisplayName("validateAndNormalize should trim spaces and hyphens from valid 12-digit Aadhaar")
    void validateAndNormalize_validAadhaar() {
        assertEquals("123456789012", aadhaarSecurityService.validateAndNormalize("1234 5678 9012"));
        assertEquals("123456789012", aadhaarSecurityService.validateAndNormalize("1234-5678-9012"));
        assertEquals("998877665544", aadhaarSecurityService.validateAndNormalize("  998877665544  "));
    }

    @Test
    @DisplayName("validateAndNormalize should throw IllegalArgumentException for invalid Aadhaar formats")
    void validateAndNormalize_invalidAadhaar_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.validateAndNormalize(null));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.validateAndNormalize(""));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.validateAndNormalize("12345"));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.validateAndNormalize("1234567890123"));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.validateAndNormalize("1234abcd9012"));
    }

    @Test
    @DisplayName("extractLast4 should extract the last 4 digits")
    void extractLast4_success() {
        assertEquals("9012", aadhaarSecurityService.extractLast4("123456789012"));
        assertEquals("5544", aadhaarSecurityService.extractLast4("998877665544"));
    }

    @Test
    @DisplayName("extractLast4 with invalid input should throw IllegalArgumentException")
    void extractLast4_invalid_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.extractLast4(null));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.extractLast4("123"));
    }

    @Test
    @DisplayName("computeHash should return deterministic 64-char hex digest using SHA")
    void computeHash_deterministic() {
        String hash1 = aadhaarSecurityService.computeHash("123456789012");
        String hash2 = aadhaarSecurityService.computeHash("123456789012");
        String diffHash = aadhaarSecurityService.computeHash("998877665544");

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, diffHash);
    }

    @Test
    @DisplayName("computeHash with null or empty input should throw IllegalArgumentException")
    void computeHash_invalidInput_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.computeHash(null));
        assertThrows(IllegalArgumentException.class, () -> aadhaarSecurityService.computeHash(""));
    }

    @Test
    @DisplayName("formatMaskedAadhaar should format with prefix XXXX XXXX")
    void formatMaskedAadhaar_success() {
        assertEquals("XXXX XXXX 9012", aadhaarSecurityService.formatMaskedAadhaar("9012"));
        assertEquals("XXXX XXXX 9012", aadhaarSecurityService.formatMaskedAadhaar("123456789012"));
        assertNull(aadhaarSecurityService.formatMaskedAadhaar(null));
        assertNull(aadhaarSecurityService.formatMaskedAadhaar("   "));
    }
}
