package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;

public interface BorrowService {
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);
    BorrowResponseDto returnBook(Long borrowId);
}
