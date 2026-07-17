package com.example.library.book.service;

import com.example.library.book.dto.BookRequest;
import com.example.library.book.dto.BookResponse;

import java.util.List;

public interface BookService {
    BookResponse createBook(BookRequest request);
    List<BookResponse> getAllBooks();
    BookResponse getBookById(Long id);
    BookResponse updateBook(Long id, BookRequest request);
    void deleteBook(Long id);
}
