package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
     * Retrieves a paginated and sorted list of books optionally filtered by category.
     * When category is null, returns all books.
     *
     * @param page Zero-based page number
     * @param size Number of records per page
     * @param sortBy Field name to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @param category Optional book category filter (null returns all)
     * @return {@link PagedResponse} containing {@link BookResponse} items
     */
    PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir, BookCategory category);

    /**
     * Searches books by matching title, author, or ISBN keywords with optional category filter and pagination.
     * When category is null, searches across all books; otherwise filters by category.
     *
     * @param query Search keyword
     * @param category Optional book category filter (null searches all categories)
     * @param page Page index
     * @param size Page size
     * @param sortBy Sort field
     * @param sortDir Sort direction
     * @return {@link PagedResponse} of matching {@link BookResponse} items
     */
    PagedResponse<BookResponse> searchBooks(String query, BookCategory category, int page, int size, String sortBy, String sortDir);

    /**
     * Retrieves all available book categories.
     *
     * @return List of all {@link BookCategory} enum constants
     */
    List<BookCategory> getAllCategories();

    /**
     * Retrieves a book by its UUID with caching support.
     *
     * @param uuid The book UUID
     * @return The {@link BookResponse} DTO
     */
    BookResponse getBookByUuid(String uuid);

    /**
     * Updates an existing book record.
     *
     * @param uuid The book UUID
     * @param request The updated book payload
     * @return The updated {@link BookResponse} DTO
     */
    BookResponse updateBook(String uuid, BookRequest request);

    /**
     * Deletes a book record from the system.
     *
     * @param uuid The book UUID to delete
     */
    void deleteBook(String uuid);

    /**
     * Uploads a cover image for a book to AWS S3.
     *
     * @param uuid The book UUID
     * @param file The multipart image file
     * @return Status message
     */
    String uploadBookCover(String uuid, MultipartFile file);

    /**
     * Retrieves the image cover URL for a specific book UUID.
     *
     * @param uuid The book UUID
     * @return The cover image URL
     */
    String getImageCoverByUuid(String uuid);
}
