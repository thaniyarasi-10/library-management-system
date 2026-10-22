package com.kovanlabs.librarymanagement.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserPaymentProfileRequestDto(
        @NotBlank(message = "Bank name is required")
        String bankName,

        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotBlank(message = "IFSC code is required")
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code format")
        String ifscCode,

        @NotBlank(message = "Card holder name is required")
        String cardHolderName,

        String cardNumber,

        String cardNetwork
) {
}
