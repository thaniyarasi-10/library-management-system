package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSyncImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private SalesforceSyncImpl salesforceSyncService;

    @InjectMocks
    private BookServiceImpl bookService;

    private Book book1;
    private Book book2;
    private String uuid1;

    @BeforeEach
    void setUp() {
        uuid1 = UUID.randomUUID().toString();

        book1 = Book.builder()
                .uuid(uuid1)
                .id(1L)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .coverImageUrl("http://s3.com/cover.jpg")
                .totalBookCount(5)
                .borrowedBookCount(1)
                .salesforceRetryCount(0)
                .build();

        book2 = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(2L)
                .title("Effective Java")
                .author("Joshua Bloch")
                .isbn("9780134685991")
                .totalBookCount(3)
                .borrowedBookCount(0)
                .salesforceRetryCount(0)
                .build();
    }

    @Test
    @DisplayName("createBook should save and dual-write to Salesforce")
    void createBook_shouldSaveAndReturnBookResponse() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "9780132350884", 5);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse response = bookService.createBook(request);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
        verify(salesforceSyncService).syncBook(any(BookSObject.class));
        verify(bookRepository, times(2)).save(any(Book.class));
    }

    @Test
    @DisplayName("createBook when Salesforce is null should still create book in database")
    void createBook_whenSalesforceIsNull_shouldCreateBook() {
        BookServiceImpl serviceWithoutSf = new BookServiceImpl(bookRepository, s3Service, null);
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "9780132350884", 5);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse response = serviceWithoutSf.createBook(request);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    @DisplayName("createBook when Salesforce fails should still return response and mark pending")
    void createBook_whenSalesforceFails_shouldStillReturnResponseAndMarkPending() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "9780132350884", 5);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SF Error")).when(salesforceSyncService).syncBook(any(BookSObject.class));

        BookResponse response = bookService.createBook(request);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
        verify(bookRepository, times(2)).save(any(Book.class));
    }

    @Test
    @DisplayName("getAllBooks with Salesforce should return paged response from SOQL (including page not last)")
    void getAllBooks_paginated_withSalesforce_shouldReturnPagedResponse() {
        BookSObject sfBook = BookSObject.builder()
                .externalBookUuid(uuid1)
                .name("Clean Code")
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .coverImageUrl("http://s3.com/cover.jpg")
                .totalBookCount(5)
                .borrowedBookCount(0)
                .errors(null)
                .build();
        when(salesforceSyncService.fetchBooksFromSalesforce(2, 0)).thenReturn(List.of(sfBook));
        when(salesforceSyncService.getTotalBooksFromSalesforce()).thenReturn(10L);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 2, "title", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(10L, response.totalElements());
        assertEquals(5, response.totalPages());
        assertFalse(response.last());
    }

    @Test
    @DisplayName("getAllBooks with Salesforce returning various error combinations and null sfBook")
    void getAllBooks_withSfBooksHavingErrors_shouldLogWarnings() {
        BookSObject sfBookWithErrors = BookSObject.builder()
                .externalBookUuid(uuid1)
                .name("Clean Code")
                .errors(List.of("Field validation error"))
                .build();
        BookSObject sfBookWithEmptyErrors = BookSObject.builder()
                .externalBookUuid(UUID.randomUUID().toString())
                .name("Effective Java")
                .errors(Collections.emptyList())
                .build();

        List<BookSObject> list = new ArrayList<>();
        list.add(sfBookWithErrors);
        list.add(sfBookWithEmptyErrors);
        list.add(null);

        when(salesforceSyncService.fetchBooksFromSalesforce(10, 0)).thenReturn(list);
        when(salesforceSyncService.getTotalBooksFromSalesforce()).thenReturn(2L);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 10, "title", "asc");

        assertNotNull(response);
    }

    @Test
    @DisplayName("getAllBooks with Salesforce returning null or empty list should fallback to MySQL")
    void getAllBooks_whenSfReturnsEmptyOrNull_shouldFallbackToMySQL() {
        when(salesforceSyncService.fetchBooksFromSalesforce(10, 0)).thenReturn(List.of());
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").ascending()), 1);
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 10, "title", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());

        // Test with null
        when(salesforceSyncService.fetchBooksFromSalesforce(10, 0)).thenReturn(null);
        PagedResponse<BookResponse> responseNull = bookService.getAllBooks(0, 10, "title", "asc");
        assertNotNull(responseNull);
    }

    @Test
    @DisplayName("getAllBooks when Salesforce is null should read from MySQL with desc sort")
    void getAllBooks_whenSalesforceIsNull_shouldFetchFromMySQLWithDescSort() {
        BookServiceImpl serviceWithoutSf = new BookServiceImpl(bookRepository, s3Service, null);
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").descending()), 1);
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = serviceWithoutSf.getAllBooks(0, 10, "title", "desc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        verify(bookRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllBooks when Salesforce throws exception should fallback to MySQL")
    void getAllBooks_paginated_salesforceFailureFallbackToMySQL() {
        when(salesforceSyncService.fetchBooksFromSalesforce(10, 0)).thenThrow(new RuntimeException("SF Down"));
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").ascending()), 1);
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 10, "title", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
    }

    @Test
    @DisplayName("searchBooks with desc sort should return paged response")
    void searchBooks_withDescSort_shouldReturnPagedResponse() {
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").descending()), 1);
        when(bookRepository.searchBooks(eq("Clean"), any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.searchBooks("Clean", 0, 10, "title", "desc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    @DisplayName("searchBooks with asc sort should return paged response")
    void searchBooks_withAscSort_shouldReturnPagedResponse() {
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").ascending()), 1);
        when(bookRepository.searchBooks(eq("Clean"), any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.searchBooks("Clean", 0, 10, "title", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    @DisplayName("getBookById when book exists should return book response")
    void getBookById_whenBookExists_shouldReturnBookResponse() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        BookResponse response = bookService.getBookById(1L);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
    }

    @Test
    @DisplayName("getBookById when book not found should throw ResponseStatusException")
    void getBookById_whenBookNotFound_shouldThrowResponseStatusException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> bookService.getBookById(99L));
    }

    @Test
    @DisplayName("updateBook when book exists should update fields and sync with Salesforce")
    void updateBook_whenBookExists_shouldUpdateAndReturnResponse() {
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 12);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertEquals("Clean Architecture", response.title());
        assertEquals("9780134494166", response.isbn());
        assertEquals(12, book1.getTotalBookCount());
        assertEquals(12, response.totalBookCount());
        verify(salesforceSyncService).syncBook(any());
        assertEquals(com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus.SUCCESS, book1.getSalesforceSyncStatus());
    }

    @Test
    @DisplayName("updateBook when request totalBookCount is null should keep existing totalBookCount")
    void updateBook_withNullTotalBookCount_shouldKeepExisting() {
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166", null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertEquals(5, book1.getTotalBookCount());
        assertEquals(5, response.totalBookCount());
    }

    @Test
    @DisplayName("updateBook when Salesforce is null should update without syncing")
    void updateBook_whenSalesforceIsNull_shouldUpdateWithoutSync() {
        BookServiceImpl serviceWithoutSf = new BookServiceImpl(bookRepository, s3Service, null);
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 10);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = serviceWithoutSf.updateBook(1L, updateRequest);

        assertNotNull(response);
        assertEquals("Clean Architecture", response.title());
        assertEquals(10, book1.getTotalBookCount());
    }

    @Test
    @DisplayName("updateBook when book not found should throw ResponseStatusException")
    void updateBook_whenBookNotFound_shouldThrowResponseStatusException() {
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 10);
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> bookService.updateBook(99L, updateRequest));
    }

    @Test
    @DisplayName("updateBook when Salesforce fails should still return response and mark pending")
    void updateBook_whenSalesforceFails_shouldStillReturnResponseAndMarkPending() {
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 10);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SF update error")).when(salesforceSyncService).syncBook(any());

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertEquals("Clean Architecture", response.title());
        assertEquals(com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus.PENDING, book1.getSalesforceSyncStatus());
        assertEquals(1, book1.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("deleteBook when book exists should delete")
    void deleteBook_whenBookExists_shouldDelete() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        assertDoesNotThrow(() -> bookService.deleteBook(1L));
        verify(bookRepository, times(1)).delete(book1);
    }

    @Test
    @DisplayName("deleteBook when book not found should throw ResponseStatusException")
    void deleteBook_whenBookNotFound_shouldThrowResponseStatusException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> bookService.deleteBook(99L));
    }

    @Test
    @DisplayName("uploadBookCover should upload and save key and url")
    void uploadBookCover_shouldUploadAndSaveKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".getBytes());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenReturn(new S3UploadResponse("key123", "http://s3.com/key123"));

        String result = bookService.uploadBookCover(1L, file);

        assertEquals("Book cover updated", result);
        verify(bookRepository, times(1)).save(book1);
    }

    @Test
    @DisplayName("uploadBookCover when book not found should throw ResponseStatusException")
    void uploadBookCover_whenBookNotFound_shouldThrowResponseStatusException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".getBytes());
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> bookService.uploadBookCover(99L, file));
    }

    @Test
    @DisplayName("uploadBookCover when S3 fails should throw RuntimeException")
    void uploadBookCover_whenS3Fails_shouldThrowRuntimeException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".getBytes());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenThrow(new IOException("S3 failure"));

        assertThrows(RuntimeException.class, () -> bookService.uploadBookCover(1L, file));
    }

    @Test
    @DisplayName("getImageCoverById should return url")
    void getImageCoverById_shouldReturnUrl() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        String url = bookService.getImageCoverById(1L);

        assertEquals("http://s3.com/cover.jpg", url);
    }

    @Test
    @DisplayName("getImageCoverById when not found should throw RuntimeException")
    void getImageCoverById_whenNotFound_shouldThrowRuntimeException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> bookService.getImageCoverById(99L));
    }
}
