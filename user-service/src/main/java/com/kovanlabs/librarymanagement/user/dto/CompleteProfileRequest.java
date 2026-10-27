package com.kovanlabs.librarymanagement.user.dto;

import com.kovanlabs.librarymanagement.user.validation.ValidPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteProfileRequest(
        @NotBlank(message = "Username is mandatory")
        @Size(min = 2, max = 50, message = "Username must be between 2 and 50 characters")
        String username,

        @NotBlank(message = "Aadhaar number is mandatory")
        @Pattern(regexp = "^\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}$", message = "Aadhaar number must consist of exactly 12 numeric digits")
        String aadhaarNumber,

        @ValidPhoneNumber(
                message = "Invalid mobile number. Please enter a valid mobile number with country code"
        )
        String phone
) {}
