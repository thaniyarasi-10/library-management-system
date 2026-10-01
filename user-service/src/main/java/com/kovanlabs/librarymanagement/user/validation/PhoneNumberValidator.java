package com.kovanlabs.librarymanagement.user.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * ConstraintValidator that uses Google libphonenumber to verify that a phone number
 * is a valid, existing international number plan according to ITU standards.
 */
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or blank is considered valid here; use @NotBlank if mandatory
        if (Objects.isNull(value) || value.trim().isBlank()) {
            return true;
        }

        String trimmed = value.trim();
        // Phone number must start with '+' for international format
        if (!trimmed.startsWith("+")) {
            return false;
        }

        try {
            // Parse with default region "ZZ" (unknown/international) since '+' is present
            PhoneNumber number = PHONE_UTIL.parse(trimmed, "ZZ");
            return PHONE_UTIL.isValidNumber(number);
        } catch (NumberParseException e) {
            return false;
        }
    }
}
