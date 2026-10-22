package com.kovanlabs.librarymanagement.payment.dto;

import java.time.LocalDateTime;

public record UserPaymentProfileResponseDto(
        String uuid,
        String userUuid,
        String accountHolderName,
        String bankName,
        String maskedAccountNumber,
        String ifscCode,
        LocalDateTime updatedAt
) {
}
