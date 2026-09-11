package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;

import java.util.List;

public interface BorrowService {
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);
    BorrowResponseDto returnBook(Long borrowId);
    List<BorrowResponseDto> getAllBorrows();
    List<BorrowResponseDto> getBorrowsByUserId(Long userId);
    List<BorrowResponseDto> getBorrowsByUserEmail(String email);
}
