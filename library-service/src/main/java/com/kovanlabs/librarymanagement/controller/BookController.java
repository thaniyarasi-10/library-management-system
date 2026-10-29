package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/api/books", "/books"})
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse createBook(@Valid @RequestBody BookRequest request) {
        return bookService.createBook(request);
    }

    @GetMapping
    public PagedResponse<BookResponse> getAllBooks(
            @RequestParam(name = "category", required = false) BookCategory category,
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return bookService.getAllBooks(page, size, sortBy, sortDir, category);
    }

    @GetMapping("/search")
    public PagedResponse<BookResponse> searchBooks(
            @RequestParam(name = "query", defaultValue = "", required = false) String query,
            @RequestParam(name = "category", required = false) BookCategory category,
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return bookService.searchBooks(query, category, page, size, sortBy, sortDir);
    }

    @GetMapping("/categories")
    public List<BookCategory> getCategories() {
        return bookService.getAllCategories();
    }

    @GetMapping("/{uuid}")
    public BookResponse getBookByUuid(@PathVariable("uuid") String uuid) {
        return bookService.getBookByUuid(uuid);
    }

    @PutMapping("/{uuid}")
    public BookResponse updateBook(@PathVariable("uuid") String uuid, @Valid @RequestBody BookRequest request) {
        return bookService.updateBook(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable("uuid") String uuid) {
        bookService.deleteBook(uuid);
    }

    @PostMapping("/{uuid}/cover")
    public ResponseEntity<String> uploadCover(
            @PathVariable("uuid") String uuid,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        String key = bookService.uploadBookCover(uuid, file);
        return ResponseEntity.ok(key);
    }

    @GetMapping("/{uuid}/cover")
    public ResponseEntity<String> getBookCover(@PathVariable("uuid") String uuid) {
        return ResponseEntity.ok(bookService.getImageCoverByUuid(uuid));
    }
}
