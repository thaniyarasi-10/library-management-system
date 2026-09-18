package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.database.entity.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

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
    void testToBookSObject_and_toBookResponse() {
        UUID uuid = UUID.randomUUID();
        BookResponse dto = new BookResponse(uuid, 1L, "Clean Architecture", "Uncle Bob", "1234567890", "http://img.png");

        var sObject = bookMapper.toBookSObject(dto);
        assertNotNull(sObject);
        assertEquals(uuid.toString(), sObject.getExternalBookUuid());
        assertEquals("Clean Architecture", sObject.getTitle());
        assertEquals("Clean Architecture", sObject.getName());

        BookResponse mappedBack = bookMapper.toBookResponse(sObject);
        assertNotNull(mappedBack);
        assertEquals(uuid, mappedBack.uuid());
        assertEquals("Clean Architecture", mappedBack.title());
        assertEquals("Uncle Bob", mappedBack.author());
    }

    @Test
    void testToBookResponseList() {
        var s1 = com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject.builder()
                .externalBookUuid(UUID.randomUUID().toString())
                .title("Title 1")
                .build();
        var s2 = com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject.builder()
                .externalBookUuid(UUID.randomUUID().toString())
                .title("Title 2")
                .build();

        List<BookResponse> responses = bookMapper.toBookResponseList(List.of(s1, s2));
        assertNotNull(responses);
        assertEquals(2, responses.size());
    }

    @Test
    void testToBookResponseList_Null() {
        assertNull(bookMapper.toBookResponseList(null));
    }

    @Test
    void testHelperMethods() {
        assertNull(bookMapper.parseUUID(null));
        assertNull(bookMapper.parseUUID("invalid-uuid"));
        assertNotNull(bookMapper.parseUUID(UUID.randomUUID().toString()));
    }
}
