package com.kovanlabs.librarymanagement.fine.dto;

import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FineResponseDto(
        UUID uuid,
        Long id,
        UUID bookUuid,
        UUID userUuid,
        BigDecimal pendingFineAmount,
        FineStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
