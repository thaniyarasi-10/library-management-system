package com.kovanlabs.librarymanagement.salesforce.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.salesforce.constant.BookFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.BorrowFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.ContactFieldConstants;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SalesforceMapperTest {

    private SalesforceMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = new SalesforceMapper();
        objectMapper = new ObjectMapper();
    }

    @Test
    void toContactFields_withValidUser_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        User user = User.builder().uuid(uuid).name("John Doe").email("john@example.com").build();

        Map<String, Object> fields = mapper.toContactFields(user);

        assertNotNull(fields);
        assertEquals("John Doe", fields.get(ContactFieldConstants.LAST_NAME));
        assertEquals("john@example.com", fields.get(ContactFieldConstants.EMAIL));
        assertEquals(uuid.toString(), fields.get(ContactFieldConstants.EXTERNAL_USER_UUID));
    }

    @Test
    void toContactFields_withBlankName_usesEmail() {
        User user = User.builder().uuid(UUID.randomUUID()).name("   ").email("blank@example.com").build();
        Map<String, Object> fields = mapper.toContactFields(user);
        assertEquals("blank@example.com", fields.get(ContactFieldConstants.LAST_NAME));
    }

    @Test
    void toContactFields_nullUser_returnsEmptyMap() {
        assertTrue(mapper.toContactFields(null).isEmpty());
    }

    @Test
    void toUserResponse_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ContactFieldConstants.EXTERNAL_USER_UUID, uuid.toString());
        node.put(ContactFieldConstants.LAST_NAME, "Jane");
        node.put(ContactFieldConstants.EMAIL, "jane@example.com");

        UserResponse res = mapper.toUserResponse(node);
        assertNotNull(res);
        assertEquals(uuid, res.uuid());
        assertEquals("Jane", res.name());
        assertEquals("jane@example.com", res.email());
    }

    @Test
    void toBookFields_withValidBook_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        Book book = Book.builder()
                .uuid(uuid)
                .title("Clean Architecture")
                .author("Uncle Bob")
                .isbn("1234567890")
                .coverImageUrl("http://cover.jpg")
                .build();

        Map<String, Object> fields = mapper.toBookFields(book);
        assertNotNull(fields);
        assertEquals("Clean Architecture", fields.get(BookFieldConstants.NAME));
        assertEquals("Clean Architecture", fields.get(BookFieldConstants.TITLE));
        assertEquals("Uncle Bob", fields.get(BookFieldConstants.AUTHOR));
        assertEquals("1234567890", fields.get(BookFieldConstants.ISBN));
        assertEquals("http://cover.jpg", fields.get(BookFieldConstants.COVER_IMAGE_URL));
        assertEquals(uuid.toString(), fields.get(BookFieldConstants.EXTERNAL_BOOK_UUID));
    }

    @Test
    void toBorrowFields_mapsCorrectly() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        User user = User.builder().uuid(userUuid).name("Alice").email("alice@example.com").build();
        Book book = Book.builder().uuid(bookUuid).title("Domain-Driven Design").author("Eric Evans").build();
        Borrow borrow = Borrow.builder()
                .uuid(borrowUuid)
                .user(user)
                .book(book)
                .borrowDate(now)
                .dueDate(now.plusDays(14))
                .returnedDate(now.plusDays(7))
                .status(BorrowStatus.RETURNED)
                .build();

        Map<String, Object> fields = mapper.toBorrowFields(borrow);
        assertNotNull(fields);
        assertEquals(borrowUuid.toString(), fields.get(BorrowFieldConstants.EXTERNAL_BORROW_UUID));
        assertEquals(now.toString(), fields.get(BorrowFieldConstants.BORROW_DATE));
        assertEquals("RETURNED", fields.get(BorrowFieldConstants.BORROW_STATUS));
        assertEquals(Map.of(ContactFieldConstants.EXTERNAL_USER_UUID, userUuid.toString()), fields.get(BorrowFieldConstants.CONTACT_RELATION));
        assertEquals(Map.of(BookFieldConstants.EXTERNAL_BOOK_UUID, bookUuid.toString()), fields.get(BorrowFieldConstants.BOOK_RELATION));
    }

    @Test
    void listMappers_withNullOrEmpty_returnsEmptyList() {
        assertTrue(mapper.toUserResponseList(null).isEmpty());
    }
}
