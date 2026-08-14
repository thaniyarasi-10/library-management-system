package com.kovanlabs.librarymanagement.borrow.controller;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.service.BorrowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/search")
    public PagedResponse<BorrowResponseDto> searchBorrowedBooks(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return borrowService.searchBorrowedBooks(query, page, size, sortBy, sortDir);
    }
}
