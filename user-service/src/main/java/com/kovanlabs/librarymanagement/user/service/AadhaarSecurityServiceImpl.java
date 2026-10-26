package com.kovanlabs.librarymanagement.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Service for Aadhaar validation, masking, and HMAC-SHA256 digest generation.
 * Complete Aadhaar numbers are never logged or stored in plaintext.
 */
@Slf4j
@Service
public class AadhaarSecurityServiceImpl implements AadhaarSecurityService {

    private static final Pattern TWELVE_DIGITS_PATTERN = Pattern.compile("^\\d{12}$");
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String MASK_PREFIX = "XXXX XXXX ";

    private final String hmacSecret;

    public AadhaarSecurityServiceImpl(@Value("${aadhaar.hmac-secret:${AADHAAR_HMAC_SECRET}}") String hmacSecret) {
        if (Objects.isNull(hmacSecret) || hmacSecret.isBlank()) {
            throw new IllegalStateException("Aadhaar HMAC secret key is not configured");
        }
        this.hmacSecret = hmacSecret.trim();
    }

    @Override
    public String validateAndNormalize(String rawAadhaar) {
        if (rawAadhaar == null) {
            throw new IllegalArgumentException("Aadhaar number is mandatory");
        }
        String clean = rawAadhaar.replaceAll("[\\s-]+", "");
        if (!TWELVE_DIGITS_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid Aadhaar format. Aadhaar must consist of exactly 12 numeric digits.");
        }
        return clean;
    }

    @Override
    public String extractLast4(String normalizedAadhaar) {
        if (normalizedAadhaar == null || normalizedAadhaar.length() < 4) {
            throw new IllegalArgumentException("Cannot extract last 4 digits from invalid Aadhaar");
        }
        return normalizedAadhaar.substring(normalizedAadhaar.length() - 4);
    }

    @Override
    public String computeHash(String normalizedAadhaar) {
        if (normalizedAadhaar == null || normalizedAadhaar.isBlank()) {
            throw new IllegalArgumentException("Aadhaar number cannot be empty when computing SHA hash");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(normalizedAadhaar.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to compute SHA hash for Aadhaar: {}", e.getMessage());
            throw new IllegalStateException("Cryptographic error computing Aadhaar SHA hash", e);
        }
    }

    @Override
    public String formatMaskedAadhaar(String last4) {
        if (last4 == null || last4.isBlank()) {
            return null;
        }
        String cleanLast4 = last4.trim();
        if (cleanLast4.length() > 4) {
            cleanLast4 = cleanLast4.substring(cleanLast4.length() - 4);
        }
        return MASK_PREFIX + cleanLast4;
    }
}
