package com.kovanlabs.librarymanagement.book.controller;

import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.book.service.BorrowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/borrow")
public class BorrowController {

    private final BorrowService borrowService;
    public BorrowController(BorrowService borrowService){
        this.borrowService = borrowService;
    }

    @PostMapping
    public ResponseEntity<BorrowResponseDto> borrowBook(@RequestBody BorrowRequestDto request) {
        BorrowResponseDto response = borrowService.borrowBook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{borrowId}")
    public BorrowResponseDto returnBook(@PathVariable("borrowId") Long borrowId){
        return borrowService.returnBook(borrowId);
    }
}
