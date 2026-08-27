package com.kovanlabs.librarymanagement.book.mapping;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookMapperTest {

    private BookMapper bookMapper;

    @BeforeEach
    void setUp() {
        bookMapper = Mappers.getMapper(BookMapper.class);
    }

    @Test
    void testMapToResponse_SingleBook() {
        UUID uuid = UUID.randomUUID();
        Book book = Book.builder()
                .uuid(uuid)
                .id(10L)
                .title("Effective Java")
                .author("Joshua Bloch")
                .isbn("978-0134685991")
                .build();

        BookResponse response = bookMapper.mapToResponse(book);

        assertNotNull(response);
        assertEquals(uuid, response.uuid());
        assertEquals(10L, response.id());
        assertEquals("Effective Java", response.title());
        assertEquals("Joshua Bloch", response.author());
        assertEquals("978-0134685991", response.isbn());
    }

    @Test
    void testMapToResponse_NullBook() {
        assertNull(bookMapper.mapToResponse((Book) null));
    }

    @Test
    void testMapToResponse_BookList() {
        Book book1 = Book.builder().id(1L).title("Book 1").build();
        Book book2 = Book.builder().id(2L).title("Book 2").build();

        List<BookResponse> responses = bookMapper.mapToResponse(List.of(book1, book2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Book 1", responses.get(0).title());
        assertEquals("Book 2", responses.get(1).title());
    }

    @Test
    void testMapToResponse_NullBookList() {
        List<BookResponse> responses = bookMapper.mapToResponse((List<Book>) null);
        assertNull(responses);
    }

    @Test
    void testMapToResponse_SingleBorrow() {
        UUID borrowUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        Book book = Book.builder().uuid(bookUuid).build();
        User user = User.builder().uuid(userUuid).build();
        Borrow borrow = Borrow.builder()
                .uuid(borrowUuid)
                .id(5L)
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();

        BorrowResponseDto response = bookMapper.mapToResponse(borrow);

        assertNotNull(response);
        assertEquals(borrowUuid, response.borrowUuid());
        assertEquals(5L, response.id());
        assertEquals(bookUuid, response.bookId());
        assertEquals(userUuid, response.userId());
        assertEquals(BorrowStatus.BORROWED, response.status());
    }

    @Test
    void testMapToResponse_NullBorrow() {
        assertNull(bookMapper.mapToResponse((Borrow) null));
    }

    @Test
    void testMapToResponse_BorrowList() {
        Borrow b1 = Borrow.builder().id(1L).status(BorrowStatus.BORROWED).build();
        Borrow b2 = Borrow.builder().id(2L).status(BorrowStatus.RETURNED).build();

        List<BorrowResponseDto> responses = bookMapper.mapToResponseForBorrows(List.of(b1, b2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(BorrowStatus.BORROWED, responses.get(0).status());
        assertEquals(BorrowStatus.RETURNED, responses.get(1).status());
    }

    @Test
    void testMapToResponse_NullBorrowList() {
        List<BorrowResponseDto> responses = bookMapper.mapToResponseForBorrows((List<Borrow>) null);
        assertNull(responses);
    }

    @Test
    void testMapToEntity_BookRequest() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "978-0132350884");

        Book book = bookMapper.mapToEntity(request);

        assertNotNull(book);
        assertEquals("Clean Code", book.getTitle());
        assertEquals("Robert C. Martin", book.getAuthor());
        assertEquals("978-0132350884", book.getIsbn());
    }

    @Test
    void testMapToEntity_NullBookRequest() {
        assertNull(bookMapper.mapToEntity((BookRequest) null));
    }

    @Test
    void testMapToEntity_BorrowRequest() {
        BorrowRequestDto request = new BorrowRequestDto(100L, 200L);
        Book book = Book.builder().id(100L).build();
        User user = User.builder().id(200L).build();

        Borrow borrow = bookMapper.mapToEntity(request, book, user);

        assertNotNull(borrow);
        assertEquals(book, borrow.getBook());
        assertEquals(user, borrow.getUser());
        assertEquals(BorrowStatus.BORROWED, borrow.getStatus());
        assertEquals(LocalDate.now(), borrow.getBorrowDate());
        assertEquals(LocalDate.now().plusDays(14), borrow.getDueDate());
    }
}
