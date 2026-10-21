package com.kovanlabs.librarymanagement.dto;

public record PaymentOrderResponse(
        String orderId,
        Long amount,
        String keyId,
        String paymentType,
        String referenceId
) {
}
