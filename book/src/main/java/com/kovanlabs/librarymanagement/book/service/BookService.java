package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BookService {
    BookResponse createBook(BookRequest request);
    List<BookResponse> getAllBooks();
    PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir);
    PagedResponse<BookResponse> searchBooks(String query, int page, int size, String sortBy, String sortDir);
    BookResponse getBookById(Long id);
    BookResponse updateBook(Long id, BookRequest request);
    void deleteBook(Long id);
    String uploadBookCover(Long bookId, MultipartFile file);
    String getImageCoverById(Long id);
    void deleteBookCover(Long bookId);
}
