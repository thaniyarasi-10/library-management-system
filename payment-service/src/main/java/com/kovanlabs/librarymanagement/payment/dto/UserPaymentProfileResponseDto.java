package com.kovanlabs.librarymanagement.payment.dto;

import java.time.LocalDateTime;

public record UserPaymentProfileResponseDto(
        String uuid,
        String userUuid,
        String cardHolderName,
        String bankName,
        String maskedAccountNumber,
        String ifscCode,
        String cardLastFour,
        String cardNetwork,
        LocalDateTime updatedAt
) {
}
