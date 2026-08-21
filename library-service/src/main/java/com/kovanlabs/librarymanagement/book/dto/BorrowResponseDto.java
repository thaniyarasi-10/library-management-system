package com.kovanlabs.librarymanagement.book.dto;

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
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnedDate,
        BorrowStatus status
) {
}
