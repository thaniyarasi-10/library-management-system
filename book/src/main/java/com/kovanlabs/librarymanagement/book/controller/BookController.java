package com.kovanlabs.librarymanagement.book.controller;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse createBook( @RequestBody BookRequest request) {
        return bookService.createBook(request);
    }

    @GetMapping
    public PagedResponse<BookResponse> getAllBooks(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return bookService.getAllBooks(page, size, sortBy, sortDir);
    }

    @GetMapping("/search")
    public PagedResponse<BookResponse> searchBooks(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return bookService.searchBooks(query, page, size, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    public BookResponse getBookById(@PathVariable("id") Long id) {
        return bookService.getBookById(id);
    }

    @PutMapping("/{id}")
    public BookResponse updateBook(@PathVariable("id") Long id, @RequestBody BookRequest request) {
        return bookService.updateBook(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable("id") Long id) {
        bookService.deleteBook(id);
    }

    @PostMapping("/{bookId}/cover")
    public ResponseEntity<String> uploadCover(
            @PathVariable("bookId") Long bookId,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        String key = bookService.uploadBookCover(bookId, file);
        return ResponseEntity.ok(key);
    }

    @GetMapping("/{bookId}/cover")
    public ResponseEntity<String> getBookCover(@PathVariable("bookId") Long bookId) {
        return ResponseEntity.ok(bookService.getImageCoverById(bookId));
    }

    @DeleteMapping("/{bookId}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBookCover(@PathVariable("bookId") Long bookId) {
        bookService.deleteBookCover(bookId);
    }
}
