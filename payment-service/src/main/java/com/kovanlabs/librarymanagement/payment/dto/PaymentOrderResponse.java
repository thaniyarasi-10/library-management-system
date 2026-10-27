package com.kovanlabs.librarymanagement.payment.dto;

public record PaymentOrderResponse(
        String orderId,
        Long amount,
        String keyId,
        String paymentType,
        String referenceId
) {
}
