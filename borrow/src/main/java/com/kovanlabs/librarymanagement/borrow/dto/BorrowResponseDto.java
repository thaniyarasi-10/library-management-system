package com.kovanlabs.librarymanagement.borrow.dto;

import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record BorrowResponseDto(Long borrowId, Long userId, Long bookId, LocalDate borrowDate, LocalDate dueDate, LocalDate returnedDate, BorrowStatus status) {
}
