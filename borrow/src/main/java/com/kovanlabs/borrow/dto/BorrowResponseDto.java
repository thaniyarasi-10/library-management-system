package com.kovanlabs.borrow.dto;

import com.kovanlabs.borrow.enums.BorrowStatus;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record BorrowResponseDto(Long borrowId, Long userId, Long bookId, LocalDate borrowDate, LocalDate dueDate, LocalDate returnedDate, BorrowStatus status) {

}
