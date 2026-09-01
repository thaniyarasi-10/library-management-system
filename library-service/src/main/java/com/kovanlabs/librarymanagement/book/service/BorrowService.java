package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;

import java.util.List;

public interface BorrowService {
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);
    BorrowResponseDto returnBook(Long borrowId);
    List<BorrowResponseDto> getAllBorrows();
    List<BorrowResponseDto> getBorrowsByUserId(Long userId);
    List<BorrowResponseDto> getBorrowsByUserEmail(String email);
}
