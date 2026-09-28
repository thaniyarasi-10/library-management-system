package com.kovanlabs.librarymanagement.dto;

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
) {}

