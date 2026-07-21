package com.kovanlabs.borrow.controller;

import com.kovanlabs.borrow.dto.BorrowRequestDto;
import com.kovanlabs.borrow.dto.BorrowResponseDto;
import com.kovanlabs.borrow.service.BorrowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/borrow")
public class BorrowController {

    private BorrowService borrowService;
    public BorrowController(BorrowService borrowService){
        this.borrowService= borrowService;
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
