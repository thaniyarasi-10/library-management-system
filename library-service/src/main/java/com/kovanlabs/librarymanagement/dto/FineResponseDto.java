package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record FineResponseDto(
        String uuid,
        Long id,
        String bookUuid,
        Long bookNumericId,
        String bookTitle,
        String bookAuthor,
        String bookCoverImageUrl,
        String userUuid,
        Long userNumericId,
        String userName,
        String userEmail,
        BigDecimal amount,
        BigDecimal pendingFineAmount,
        FineStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

