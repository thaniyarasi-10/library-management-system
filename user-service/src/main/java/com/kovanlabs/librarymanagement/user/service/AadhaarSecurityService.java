package com.kovanlabs.librarymanagement.user.service;

public interface AadhaarSecurityService {

    /**
     * Normalizes and validates that the raw Aadhaar input consists of exactly 12 numeric digits.
     * Whitespace and hyphens may be stripped prior to validation.
     *
     * @param rawAadhaar raw Aadhaar input string
     * @return clean 12-digit string
     * @throws IllegalArgumentException if invalid
     */
    String validateAndNormalize(String rawAadhaar);

    /**
     * Extracts the last 4 digits of a valid 12-digit Aadhaar number.
     *
     * @param normalizedAadhaar 12-digit Aadhaar number
     * @return 4-digit suffix
     */
    String extractLast4(String normalizedAadhaar);

    /**
     * Computes a SHA-256 hex digest of the normalized Aadhaar number.
     * Uses HMAC-SHA256 with the external secret key (or SHA-256 digest) so that
     * full Aadhaar is hashed securely.
     *
     * @param normalizedAadhaar 12-digit Aadhaar number
     * @return 64-character lowercase hex digest
     */
    String computeHash(String normalizedAadhaar);

    /**
     * Formats a 4-digit suffix into standard masked Aadhaar display format: "XXXX XXXX 1234".
     *
     * @param last4 4-digit suffix
     * @return masked Aadhaar display string or null if input is null/empty
     */
    String formatMaskedAadhaar(String last4);
}
