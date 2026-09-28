package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record BorrowResponseDto(
        UUID borrowUuid,
        Long id,
        UUID userId,
        UUID bookId,
        Long bookNumericId,
        String bookTitle,
        String bookAuthor,
        String bookCoverImageUrl,
        Long userNumericId,
        String userName,
        String userEmail,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnedDate,
        BorrowStatus status
) {}

