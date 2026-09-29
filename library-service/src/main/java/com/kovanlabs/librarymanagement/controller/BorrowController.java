package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.service.BorrowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/borrow")
public class BorrowController {

    private final BorrowService borrowService;
    public BorrowController(BorrowService borrowService){
        this.borrowService = borrowService;
    }

    @GetMapping
    public List<BorrowResponseDto> getAllBorrows() {
        return borrowService.getAllBorrows();
    }

    @GetMapping("/user/{userId}")
    public List<BorrowResponseDto> getBorrowsByUserId(@PathVariable("userId") Long userId) {
        return borrowService.getBorrowsByUserId(userId);
    }

    @GetMapping("/me")
    public List<BorrowResponseDto> getMyBorrows(java.security.Principal principal) {
        return Objects.isNull(principal)
                ? Collections.emptyList()
                : borrowService.getBorrowsByUserEmail(principal.getName());
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
