package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.mapping.BookMapper;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final S3Service s3Service;
    private final BookMapper bookMapper;
    private final SalesforceSyncService salesforceSyncService;

    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public BookResponse createBook(BookRequest request) {
        Book book = bookMapper.mapToEntity(request);
        Book savedBook = bookRepository.save(book);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBook(savedBook);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for book creation: {}", e.getMessage());
            }
        }

        return bookMapper.mapToResponse(savedBook);
    }

    @Override
    @Cacheable(value = "books")
    public List<BookResponse> getAllBooks() {
        if (salesforceSyncService != null) {
            try {
                List<BookResponse> sfBooks = salesforceSyncService.fetchBooksFromSalesforce();
                if (sfBooks != null && !sfBooks.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} books from Salesforce SOQL", sfBooks.size());
                    return sfBooks;
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for books, falling back to MySQL: {}", e.getMessage());
            }
        }
        log.info("[DATA SOURCE: MYSQL] Fetching books from MySQL database");
        return bookMapper.mapToResponse(bookRepository.findAll());
    }

    @Override
    public PagedResponse<BookResponse> getAllBooks(int page, int size, String sortBy, String sortDir) {
        if (salesforceSyncService != null) {
            try {
                List<BookResponse> sfBooks = salesforceSyncService.fetchBooksFromSalesforce();
                if (sfBooks != null && !sfBooks.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} books from Salesforce SOQL (paging in memory)", sfBooks.size());
                    int fromIndex = Math.min(page * size, sfBooks.size());
                    int toIndex = Math.min(fromIndex + size, sfBooks.size());
                    List<BookResponse> pageContent = sfBooks.subList(fromIndex, toIndex);
                    int totalPages = (int) Math.ceil((double) sfBooks.size() / size);
                    return new PagedResponse<>(
                            pageContent,
                            page,
                            size,
                            (long) sfBooks.size(),
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

    @Override
    @Cacheable(value = "books", key = "#p0")
    public BookResponse getBookById(Long id) {
        log.info("CACHE MISS - Fetching book {} from DATABASE", id);
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        return bookMapper.mapToResponse(book);
    }

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

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBook(updatedBook);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for book update: {}", e.getMessage());
            }
        }

        return bookMapper.mapToResponse(updatedBook);
    }

    @Override
    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        bookRepository.delete(book);
    }

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

    public String getImageCoverById(Long id){
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));

        return book.getCoverImageUrl();
    }
}
