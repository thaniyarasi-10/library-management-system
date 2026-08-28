package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.book.mapping.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private S3Service s3Service;

    @Spy
    private BookMapper bookMapper = Mappers.getMapper(BookMapper.class);

    @InjectMocks
    private BookServiceImpl bookService;

    private Book book1;
    private Book book2;
    private UUID uuid1;

    @BeforeEach
    void setUp() {
        uuid1 = UUID.randomUUID();

        book1 = Book.builder()
                .uuid(uuid1)
                .id(1L)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .coverImageUrl("http://s3.com/cover.jpg")
                .build();

        book2 = Book.builder()
                .uuid(UUID.randomUUID())
                .id(2L)
                .title("Effective Java")
                .author("Joshua Bloch")
                .isbn("9780134685991")
                .build();
    }

    @Test
    void createBook_shouldSaveAndReturnBookResponse() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "9780132350884");
        when(bookRepository.save(any(Book.class))).thenReturn(book1);

        BookResponse response = bookService.createBook(request);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
    }

    @Test
    void getAllBooks_shouldReturnList() {
        when(bookRepository.findAll()).thenReturn(List.of(book1, book2));

        List<BookResponse> responses = bookService.getAllBooks();

        assertEquals(2, responses.size());
    }

    @Test
    void getAllBooks_paginated_shouldReturnPagedResponse() {
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").ascending()), 1);
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 10, "title", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
    }

    @Test
    void searchBooks_shouldReturnPagedResponse() {
        Page<Book> bookPage = new PageImpl<>(List.of(book1), PageRequest.of(0, 10, Sort.by("title").descending()), 1);
        when(bookRepository.searchBooks(eq("Clean"), any(Pageable.class))).thenReturn(bookPage);

        PagedResponse<BookResponse> response = bookService.searchBooks("Clean", 0, 10, "title", "desc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    void getBookById_whenBookExists_shouldReturnBookResponse() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        BookResponse response = bookService.getBookById(1L);

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
    }

    @Test
    void getBookById_whenBookNotFound_shouldThrowResponseStatusException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> bookService.getBookById(99L));
    }

    @Test
    void updateBook_whenBookExists_shouldUpdateAndReturnResponse() {
        BookRequest updateRequest = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertEquals("Clean Architecture", response.title());
        assertEquals("9780134494166", response.isbn());
    }

    @Test
    void deleteBook_whenBookExists_shouldDelete() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        assertDoesNotThrow(() -> bookService.deleteBook(1L));
        verify(bookRepository, times(1)).delete(book1);
    }

    @Test
    void uploadBookCover_shouldUploadAndSaveKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".getBytes());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenReturn(new S3UploadResponse("key123", "http://s3.com/key123"));

        String result = bookService.uploadBookCover(1L, file);

        assertEquals("Book cover updated", result);
        verify(bookRepository, times(1)).save(book1);
    }

    @Test
    void uploadBookCover_whenS3Fails_shouldThrowRuntimeException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".getBytes());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenThrow(new IOException("S3 failure"));

        assertThrows(RuntimeException.class, () -> bookService.uploadBookCover(1L, file));
    }

    @Test
    void getImageCoverById_shouldReturnUrl() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        String url = bookService.getImageCoverById(1L);

        assertEquals("http://s3.com/cover.jpg", url);
    }

    @Test
    void getImageCoverById_whenNotFound_shouldThrowRuntimeException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> bookService.getImageCoverById(99L));
    }
}
