package com.kovanlabs.librarymanagement.payment.dto;

import java.util.List;

public record PaymentHistoryResponseDto(
        Long totalPaid,
        Long totalRefunded,
        Long netPaid,
        long successfulTransactionCount,
        List<PaymentResponseDto> content,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious
) {
}
