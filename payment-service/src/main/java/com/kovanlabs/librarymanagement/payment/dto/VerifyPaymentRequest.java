package com.kovanlabs.librarymanagement.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank(message = "Razorpay order ID is required")
        String razorpayOrderId,

        String razorpayPaymentId,

        String razorpaySignature
) {
}