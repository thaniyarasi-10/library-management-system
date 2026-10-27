package com.kovanlabs.librarymanagement.payment.dto;

import java.time.LocalDateTime;
import java.util.List;

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
                String status,
                LocalDateTime createdAt,
                LocalDateTime updatedAt,
                List<PaymentResponseDto> refunds) {
}
