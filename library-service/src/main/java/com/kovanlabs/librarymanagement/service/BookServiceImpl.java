package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BookService} with Redis caching, S3 cover image management,
 * and dual-write/read operations integrated with Salesforce SObjects.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final S3Service s3Service;
    private final BookMapper bookMapper;
    private final SalesforceSyncService salesforceSyncService;

    /**
     * Creates a new book record, saves it to the database, and synchronizes with Salesforce.
     *
     * @param request Book payload
     * @return Created {@link BookResponse}
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public BookResponse createBook(BookRequest request) {
        Book book = bookMapper.mapToEntity(request);
        Book savedBook = bookRepository.save(book);
        BookResponse response = bookMapper.mapToResponse(savedBook);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBook(bookMapper.toBookSObject(response));
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for book creation: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Retrieves paginated books from Salesforce SOQL if configured, otherwise falls back to MySQL.
     *
     * @param page Zero-based page number
     * @param size Number of items per page
     * @param sortBy Field name to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} of {@link BookResponse}
     */
    @Override
    public PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir) {
        if (salesforceSyncService != null) {
            try {
                int offset = page * size;
                var sfBookModels = salesforceSyncService.fetchBooksFromSalesforce(size, offset);
                long totalBooks = salesforceSyncService.getTotalBooksFromSalesforce();

                if (sfBookModels != null && !sfBookModels.isEmpty()) {
                    List<BookResponse> sfBooks = bookMapper.toBookResponseList(sfBookModels);
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} books from Salesforce SOQL", sfBooks.size());
                    int totalPages = (int) Math.ceil((double) totalBooks / size);
                    return new PagedResponse<>(
                            sfBooks,
                            page,
                            size,
                            totalBooks,
                            totalPages,
                            page >= totalPages - 1
                    );
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for books, falling back to MySQL: {}", e.getMessage());
            }
        }

        log.info("[DATA SOURCE: MYSQL] Fetching paged books (page={}, size={}) from MySQL database", page, size);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Book> booksPage = bookRepository.findAll(pageable);
        List<BookResponse> content = booksPage.getContent().stream()
                .map(bookMapper::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    /**
     * Searches books matching the search query in MySQL with pagination.
     *
     * @param query Search query
     * @param page Page index
     * @param size Page size
     * @param sortBy Sort field
     * @param sortDir Sort direction
     * @return {@link PagedResponse} of matching {@link BookResponse} items
     */
    @Override
    public PagedResponse<BookResponse> searchBooks(String query, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Book> booksPage = bookRepository.searchBooks(query, pageable);
        List<BookResponse> content = booksPage.getContent().stream()
                .map(bookMapper::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    /**
     * Retrieves a book by ID with Redis caching.
     *
     * @param id The book ID
     * @return {@link BookResponse} DTO
     */
    @Override
    @Cacheable(value = "books", key = "#p0")
    public BookResponse getBookById(Long id) {
        log.info("CACHE MISS - Fetching book {} from DATABASE", id);
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        return bookMapper.mapToResponse(book);
    }

    /**
     * Updates an existing book and triggers synchronization with Salesforce.
     *
     * @param id The book ID
     * @param request The updated book payload
     * @return Updated {@link BookResponse}
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public BookResponse updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        
        Book updatedBook = bookRepository.save(book);
        BookResponse response = bookMapper.mapToResponse(updatedBook);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBook(bookMapper.toBookSObject(response));
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for book update: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Deletes a book by ID and evicts all book caches.
     *
     * @param id The book ID to delete
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        bookRepository.delete(book);
    }

    /**
     * Uploads a book cover image to AWS S3 and updates the book entity.
     *
     * @param bookId The book ID
     * @param file The multipart image file
     * @return Status message
     */
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public String uploadBookCover(Long bookId, MultipartFile file)  {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + bookId));

        try {
            S3UploadResponse response = s3Service.uploadFile(file);

            book.setCoverImageKey(response.coverImageKey());
            book.setCoverImageUrl(response.coverImageUrl());

            bookRepository.save(book);

            return "Book cover updated";

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the cover image URL for a book by its ID.
     *
     * @param id The book ID
     * @return The cover image URL
     */
    public String getImageCoverById(Long id){
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));

        return book.getCoverImageUrl();
    }
}
