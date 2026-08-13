package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private BookServiceImpl bookService;

    private Book book1;
    private Book book2;

    @BeforeEach
    void setUp() {
        book1 = Book.builder()
                .id(1L)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .build();

        book2 = Book.builder()
                .id(2L)
                .title("Effective Java")
                .author("Joshua Bloch")
                .isbn("9780134685991")
                .build();
    }

    @Test
    @DisplayName("getBookById when book exists should return BookResponse")
    void getBookById_WhenBookExists_ShouldReturnBookResponse() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        BookResponse response = bookService.getBookById(1L);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Clean Code", response.title());
        assertEquals("Robert C. Martin", response.author());
        assertEquals("9780132350884", response.isbn());
        verify(bookRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getBookById when book not found should throw ResponseStatusException NOT_FOUND")
    void getBookById_WhenBookNotFound_ShouldThrowResponseStatusException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookService.getBookById(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Book not found with ID: 99"));
        verify(bookRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("getAllBooks unpaginated should return list of BookResponse")
    void getAllBooks_Unpaginated_ShouldReturnListOfBookResponses() {
        when(bookRepository.findAll()).thenReturn(List.of(book1, book2));

        List<BookResponse> responses = bookService.getAllBooks();

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Clean Code", responses.get(0).title());
        assertEquals("Effective Java", responses.get(1).title());
        verify(bookRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllBooks with pagination should return PagedResponse")
    void getAllBooks_WithPagination_ShouldReturnPagedResponse() {
        List<Book> books = List.of(book1, book2);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Book> booksPage = new PageImpl<>(books, pageable, books.size());

        when(bookRepository.findAll(any(Pageable.class))).thenReturn(booksPage);

        PagedResponse<BookResponse> response = bookService.getAllBooks(0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertEquals(0, response.pageNo());
        assertEquals(10, response.pageSize());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.last());
        assertEquals("Clean Code", response.content().get(0).title());

        verify(bookRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("updateBook when book exists should update book details and return BookResponse")
    void updateBook_WhenBookExists_ShouldUpdateAndReturnBookResponse() {
        BookRequest request = new BookRequest("Clean Architecture", "Robert C. Martin", "9780134494166");
        Book updatedBook = Book.builder()
                .id(1L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBook);

        BookResponse response = bookService.updateBook(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Clean Architecture", response.title());
        assertEquals("9780134494166", response.isbn());
        verify(bookRepository, times(1)).findById(1L);
        verify(bookRepository, times(1)).save(book1);
        assertEquals("Clean Architecture", book1.getTitle());
    }

    @Test
    @DisplayName("updateBook when book not found should throw ResponseStatusException NOT_FOUND")
    void updateBook_WhenBookNotFound_ShouldThrowResponseStatusException() {
        BookRequest request = new BookRequest("Updated Title", "Author", "12345");

        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookService.updateBook(99L, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Book not found with ID: 99"));
        verify(bookRepository, times(1)).findById(99L);
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteBook when book exists should delete S3 file and book")
    void deleteBook_WhenBookExists_ShouldDeleteBook() {
        book1.setCoverImageKey("book-cover-123.jpg");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        doNothing().when(s3Service).deleteFile("book-cover-123.jpg");
        doNothing().when(bookRepository).delete(book1);

        assertDoesNotThrow(() -> bookService.deleteBook(1L));

        verify(bookRepository, times(1)).findById(1L);
        verify(s3Service, times(1)).deleteFile("book-cover-123.jpg");
        verify(bookRepository, times(1)).delete(book1);
    }

    @Test
    @DisplayName("deleteBook when book not found should throw ResponseStatusException NOT_FOUND")
    void deleteBook_WhenBookNotFound_ShouldThrowResponseStatusException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookService.deleteBook(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Book not found with ID: 99"));
        verify(bookRepository, times(1)).findById(99L);
        verify(bookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteBookCover when cover exists should delete S3 file and reset entity fields")
    void deleteBookCover_WhenCoverExists_ShouldDeleteS3FileAndResetFields() {
        book1.setCoverImageKey("book-cover-123.jpg");
        book1.setCoverImageUrl("https://s3.amazonaws.com/book-cover-123.jpg");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        doNothing().when(s3Service).deleteFile("book-cover-123.jpg");
        when(bookRepository.saveAndFlush(any(Book.class))).thenReturn(book1);

        assertDoesNotThrow(() -> bookService.deleteBookCover(1L));

        verify(s3Service, times(1)).deleteFile("book-cover-123.jpg");
        assertNull(book1.getCoverImageKey());
        assertNull(book1.getCoverImageUrl());
        verify(bookRepository, times(1)).saveAndFlush(book1);
    }

    @Test
    @DisplayName("uploadBookCover when DB save fails should delete newly uploaded S3 file and throw exception")
    void uploadBookCover_WhenDBSaveFails_ShouldDeleteNewlyUploadedS3File() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "content".getBytes());
        S3UploadResponse response = new S3UploadResponse("new-key-123.jpg", "https://s3.com/new-key-123.jpg");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenReturn(response);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(new RuntimeException("DB Connection Error"));

        assertThrows(RuntimeException.class, () -> bookService.uploadBookCover(1L, file));

        verify(s3Service, times(1)).uploadFile(file);
        verify(s3Service, times(1)).deleteFile("new-key-123.jpg");
    }

    @Test
    @DisplayName("uploadBookCover when successful should update book with key and url")
    void uploadBookCover_WhenSuccessful_ShouldUpdateBookWithS3Details() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "content".getBytes());
        S3UploadResponse response = new S3UploadResponse("new-key-123.jpg", "https://s3.com/new-key-123.jpg");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));
        when(s3Service.uploadFile(file)).thenReturn(response);
        when(bookRepository.saveAndFlush(any(Book.class))).thenReturn(book1);

        String result = bookService.uploadBookCover(1L, file);

        assertEquals("Book cover updated", result);
        assertEquals("new-key-123.jpg", book1.getCoverImageKey());
        assertEquals("https://s3.com/new-key-123.jpg", book1.getCoverImageUrl());
        verify(s3Service, times(1)).uploadFile(file);
        verify(bookRepository, times(1)).saveAndFlush(book1);
    }
}