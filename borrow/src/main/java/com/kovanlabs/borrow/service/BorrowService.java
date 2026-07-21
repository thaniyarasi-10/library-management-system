package com.kovanlabs.borrow.service;

import com.kovanlabs.borrow.dto.BorrowRequestDto;
import com.kovanlabs.borrow.dto.BorrowResponseDto;
import org.springframework.stereotype.Service;

@Service
public interface BorrowService {
    BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto);
    BorrowResponseDto returnBook(Long borrowId);
}
