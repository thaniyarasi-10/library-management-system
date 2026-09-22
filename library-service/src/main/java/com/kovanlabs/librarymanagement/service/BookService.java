package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for book catalog management, search, cover storage, and caching.
 */
public interface BookService {

    /**
     * Creates a new book entry in the database and synchronizes with Salesforce.
     *
     * @param request The book creation payload
     * @return The created {@link BookResponse} DTO
     */
    BookResponse createBook(BookRequest request);

    /**
     * Retrieves a paginated and sorted list of books.
     *
     * @param page Zero-based page number
     * @param size Number of records per page
     * @param sortBy Field name to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} containing {@link BookResponse} items
     */
    PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir);

    /**
     * Searches books by matching title or author keywords with pagination.
     *
     * @param query Search keyword
     * @param page Page index
     * @param size Page size
     * @param sortBy Sort field
     * @param sortDir Sort direction
     * @return {@link PagedResponse} of matching {@link BookResponse} items
     */
    PagedResponse<BookResponse> searchBooks(String query, int page, int size, String sortBy, String sortDir);

    /**
     * Retrieves a book by its database ID with caching support.
     *
     * @param id The book ID
     * @return The {@link BookResponse} DTO
     */
    BookResponse getBookById(Long id);

    /**
     * Updates an existing book record.
     *
     * @param id The book ID
     * @param request The updated book payload
     * @return The updated {@link BookResponse} DTO
     */
    BookResponse updateBook(Long id, BookRequest request);

    /**
     * Deletes a book record from the system.
     *
     * @param id The book ID to delete
     */
    void deleteBook(Long id);

    /**
     * Uploads a cover image for a book to AWS S3.
     *
     * @param bookId The book ID
     * @param file The multipart image file
     * @return Status message
     */
    String uploadBookCover(Long bookId, MultipartFile file);

    /**
     * Retrieves the image cover URL for a specific book ID.
     *
     * @param id The book ID
     * @return The cover image URL
     */
    String getImageCoverById(Long id);
}
