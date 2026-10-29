package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
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

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BookService} with Redis caching, S3 cover image
 * management,
 * and dual-write/read operations integrated with Salesforce SObjects.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final S3Service s3Service;
    private final SalesforceSync salesforceSyncService;

    /**
     * Creates a new book record, saves it to the database, and synchronizes with
     * Salesforce.
     *
     * @param request Book payload
     * @return Created {@link BookResponse}
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public BookResponse createBook(BookRequest request) {
        Book book = BookMapper.INSTANCE.mapToEntity(request);
        Book savedBook = bookRepository.save(book);

        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBook(BookMapper.INSTANCE.toBookSObject(savedBook));
                savedBook.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                savedBook = bookRepository.save(savedBook);
            } catch (Exception e) {
                int retryCount = savedBook.getSalesforceRetryCount() + 1;
                savedBook.setSalesforceRetryCount(retryCount);
                savedBook.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                savedBook = bookRepository.save(savedBook);
                log.error(
                        "Salesforce dual-write failed for book creation [Book ID: {}, UUID: {}, Operation: CREATE, RetryCount: {}]: {}",
                        savedBook.getId(), savedBook.getUuid(), retryCount, e.getMessage());
            }
        }

        return BookMapper.INSTANCE.mapToResponse(savedBook);
    }

    /**
     * Retrieves paginated books optionally filtered by category.
     * When category is null, returns all books (from Salesforce if configured, otherwise MySQL).
     *
     * @param page     Zero-based page number
     * @param size     Number of items per page
     * @param sortBy   Field name to sort by
     * @param sortDir  Sort direction ("asc" or "desc")
     * @param category Optional book category filter (null returns all)
     * @return {@link PagedResponse} of {@link BookResponse}
     */
    @Override
    public PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir, BookCategory category) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        if (Objects.isNull(category) && Objects.nonNull(salesforceSyncService)) {
            try {
                int offset = page * size;
                var sfBookModels = salesforceSyncService.fetchBooksFromSalesforce(size, offset);
                long totalBooks = salesforceSyncService.getTotalBooksFromSalesforce();

                if (Objects.nonNull(sfBookModels) && !sfBookModels.isEmpty()) {
                    for (var sfBook : sfBookModels) {
                        if (Objects.nonNull(sfBook) && Objects.nonNull(sfBook.getErrors())
                                && !sfBook.getErrors().isEmpty()) {
                            log.warn("Salesforce error for Book [UUID: {}]: {}", sfBook.getExternalBookUuid(),
                                    sfBook.getErrors());
                        }
                    }
                    List<BookResponse> sfBooks = BookMapper.INSTANCE.toBookResponseList(sfBookModels);
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} books from Salesforce SOQL",
                            sfBooks.size());
                    int totalPages = (int) Math.ceil((double) totalBooks / size);
                    return new PagedResponse<>(
                            sfBooks,
                            page,
                            size,
                            totalBooks,
                            totalPages,
                            page >= totalPages - 1);
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for books, falling back to MySQL: {}",
                        e.getMessage());
            }
        }

        log.info("[DATA SOURCE: MYSQL] Fetching paged books (page={}, size={}, category={}) from MySQL database", page, size, category);
        Page<Book> booksPage = bookRepository.findByCategory(category, pageable);
        List<BookResponse> content = booksPage.getContent().stream()
                .map(BookMapper.INSTANCE::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast());
    }

    /**
     * Searches books matching the search query and optional category in MySQL with pagination.
     * When category is null, searches across all books; otherwise filters by category.
     *
     * @param query    Search query
     * @param category Optional category filter
     * @param page     Page index
     * @param size     Page size
     * @param sortBy   Sort field
     * @param sortDir  Sort direction
     * @return {@link PagedResponse} of matching {@link BookResponse} items
     */
    @Override
    public PagedResponse<BookResponse> searchBooks(String query, BookCategory category, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Book> booksPage = bookRepository.searchBooks(query, category, pageable);

        List<BookResponse> content = booksPage.getContent().stream()
                .map(BookMapper.INSTANCE::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast());
    }

    /**
     * Retrieves all available book categories.
     *
     * @return List of all {@link BookCategory} enum constants
     */
    @Override
    public List<BookCategory> getAllCategories() {
        return List.of(BookCategory.values());
    }

    /**
     * Retrieves a book by UUID with Redis caching.
     *
     * @param uuid The book UUID
     * @return {@link BookResponse} DTO
     */
    @Override
    @Cacheable(value = "books", key = "#p0")
    public BookResponse getBookByUuid(String uuid) {
        log.info("CACHE MISS - Fetching book {} from DATABASE", uuid);
        Book book = findBookEntity(uuid);
        return BookMapper.INSTANCE.mapToResponse(book);
    }

    /**
     * Updates an existing book and triggers synchronization with Salesforce.
     *
     * @param uuid    The book UUID
     * @param request The updated book payload
     * @return Updated {@link BookResponse}
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public BookResponse updateBook(String uuid, BookRequest request) {
        Book book = findBookEntity(uuid);
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        if (Objects.nonNull(request.totalBookCount())) {
            book.setTotalBookCount(request.totalBookCount());
        }
        if (Objects.nonNull(request.category())) {
            book.setCategory(request.category());
        }
        book.setSalesforceRetryCount(0);
        book.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);

        Book updatedBook = bookRepository.save(book);

        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBook(BookMapper.INSTANCE.toBookSObject(updatedBook));
                updatedBook.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                updatedBook.setSalesforceRetryCount(0);
                updatedBook = bookRepository.save(updatedBook);
            } catch (Exception e) {
                int retryCount = updatedBook.getSalesforceRetryCount() + 1;
                updatedBook.setSalesforceRetryCount(retryCount);
                updatedBook.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                updatedBook = bookRepository.save(updatedBook);
                log.error(
                        "Salesforce dual-write failed for book update [Book ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
                        updatedBook.getId(), updatedBook.getUuid(), retryCount, e.getMessage());
            }
        }

        return BookMapper.INSTANCE.mapToResponse(updatedBook);
    }

    /**
     * Deletes a book by UUID and evicts all book caches.
     *
     * @param uuid The book UUID to delete
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public void deleteBook(String uuid) {
        Book book = findBookEntity(uuid);
        bookRepository.delete(book);
    }

    /**
     * Uploads a book cover image to AWS S3 and updates the book entity.
     *
     * @param uuid The book UUID
     * @param file The multipart image file
     * @return Status message
     */
    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public String uploadBookCover(String uuid, MultipartFile file) {
        Book book = findBookEntity(uuid);

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
     * Retrieves the cover image URL for a book by its UUID.
     *
     * @param uuid The book UUID
     * @return The cover image URL
     */
    @Override
    public String getImageCoverByUuid(String uuid) {
        Book book = findBookEntity(uuid);
        return book.getCoverImageUrl();
    }

    private Book findBookEntity(String uuid) {
        if (Objects.isNull(uuid) || uuid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book UUID must not be empty");
        }
        return bookRepository.findByUuid(uuid)
                .or(() -> {
                    try {
                        return bookRepository.findById(Long.parseLong(uuid));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with UUID: " + uuid));
    }
}
