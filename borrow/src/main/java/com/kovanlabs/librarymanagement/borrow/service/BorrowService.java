package com.kovanlabs.librarymanagement.borrow.service;

import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import org.springframework.stereotype.Service;

@Service
public interface BorrowService {
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);
    BorrowResponseDto returnBook(Long borrowId);
    PagedResponse<BorrowResponseDto> searchBorrowedBooks(String query, int page, int size, String sortBy, String sortDir);

}
