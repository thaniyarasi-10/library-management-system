package com.kovanlabs.librarymanagement.payment.dto;

public record PaymentResponseDto(
        String uuid,
        Long id,
        String userUuid,
        String transactionType,
        String paymentType,
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpayRefundId,
        String paymentUuid,
        String referenceId,
        Long amount,
        String status
) {
}
