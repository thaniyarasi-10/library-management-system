package com.kovanlabs.librarymanagement.fine.dto;

import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FineResponseDto(
        UUID uuid,
        Long id,
        UUID bookUuid,
        Long bookNumericId,
        String bookTitle,
        String bookAuthor,
        String bookCoverImageUrl,
        UUID userUuid,
        Long userNumericId,
        String userName,
        String userEmail,
        BigDecimal amount,
        BigDecimal pendingFineAmount,
        FineStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements Serializable {
    public FineResponseDto(UUID uuid, Long id, UUID bookUuid, UUID userUuid, BigDecimal pendingFineAmount,
                           FineStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(uuid, id, bookUuid, null, null, null, null, userUuid, null, null, null, pendingFineAmount, pendingFineAmount, status, createdAt, updatedAt);
    }
}

