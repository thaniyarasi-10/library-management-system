package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record BorrowResponseDto(
        String borrowUuid,
        Long id,
        String userId,
        String bookId,
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

