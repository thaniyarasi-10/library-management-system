package com.kovanlabs.librarymanagement.salesforce.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SalesforceMapperTest {

    private SalesforceMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new SalesforceMapper(objectMapper);
    }

    // --- User DTO -> ContactSObject & Payload tests ---

    @Test
    void toContactSObject_withValidUserDto_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        UserResponse user = new UserResponse(uuid, 1L, "John Doe", "john@example.com", 10);

        ContactSObject sObject = mapper.toContactSObject(user);

        assertNotNull(sObject);
        assertEquals(uuid.toString(), sObject.getExternalUserUuid());
        assertEquals("John Doe", sObject.getLastName());
        assertEquals("john@example.com", sObject.getEmail());

        Map<String, Object> payload = mapper.toPayloadMap(sObject);
        assertEquals("John Doe", payload.get(ContactFields.LAST_NAME));
        assertEquals("john@example.com", payload.get(ContactFields.EMAIL));
        assertEquals(uuid.toString(), payload.get(ContactFields.EXTERNAL_USER_UUID));
    }

    @Test
    void toContactSObject_withBlankName_usesEmail() {
        UserResponse user = new UserResponse(UUID.randomUUID(), 2L, "   ", "blank@example.com", 0);
        ContactSObject sObject = mapper.toContactSObject(user);
        assertEquals("blank@example.com", sObject.getLastName());
    }

    @Test
    void toContactSObject_nullUser_returnsNull() {
        assertNull(mapper.toContactSObject(null));
        assertTrue(mapper.toPayloadMap(null).isEmpty());
    }

    @Test
    void toPayloadMap_withBookSObject_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        BookSObject sObject = BookSObject.builder()
                .externalBookUuid(uuid.toString())
                .name("Clean Architecture")
                .title("Clean Architecture")
                .author("Uncle Bob")
                .isbn("1234567890")
                .coverImageUrl("http://cover.jpg")
                .build();

        Map<String, Object> payload = mapper.toPayloadMap(sObject);
        assertEquals("Clean Architecture", payload.get(BookFields.NAME));
        assertEquals("Clean Architecture", payload.get(BookFields.TITLE));
        assertEquals("Uncle Bob", payload.get(BookFields.AUTHOR));
        assertEquals("1234567890", payload.get(BookFields.ISBN));
        assertEquals("http://cover.jpg", payload.get(BookFields.COVER_IMAGE_URL));
        assertEquals(uuid.toString(), payload.get(BookFields.EXTERNAL_BOOK_UUID));
    }

    @Test
    void toPayloadMap_withBorrowSObject_mapsCorrectly() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();

        BorrowSObject sObject = BorrowSObject.builder()
                .externalBorrowUuid(borrowUuid.toString())
                .borrowDate("2026-09-01")
                .dueDate("2026-09-15")
                .returnDate("2026-09-10")
                .borrowStatus("RETURNED")
                .contact(ContactSObject.builder().externalUserUuid(userUuid.toString()).lastName("Alice").email("alice@example.com").build())
                .book(BookSObject.builder().externalBookUuid(bookUuid.toString()).title("DDD").build())
                .build();

        Map<String, Object> payload = mapper.toPayloadMap(sObject);
        assertEquals(borrowUuid.toString(), payload.get(BorrowFields.EXTERNAL_BORROW_UUID));
        assertEquals("2026-09-01", payload.get(BorrowFields.BORROW_DATE));
        assertEquals("RETURNED", payload.get(BorrowFields.BORROW_STATUS));
        assertNotNull(payload.get(BorrowFields.CONTACT_RELATION));
        assertNotNull(payload.get(BorrowFields.BOOK_RELATION));
    }

    // --- ContactSObject -> UserResponse tests ---

    @Test
    void toUserResponse_mapsCorrectly() {
        UUID uuid = UUID.randomUUID();
        ContactSObject contact = ContactSObject.builder()
                .externalUserUuid(uuid.toString())
                .lastName("Jane")
                .email("jane@example.com")
                .build();

        UserResponse res = mapper.toUserResponse(contact);
        assertNotNull(res);
        assertEquals(uuid, res.uuid());
        assertEquals("Jane", res.name());
        assertEquals("jane@example.com", res.email());
    }

    // --- JSON deserialization tests via @JsonProperty ---

    @Test
    void fromJsonNode_deserializesSObjectWithJsonProperty() throws Exception {
        String jsonStr = "{\"External_Book_UUID__c\":\"" + UUID.randomUUID() + "\",\"Name\":\"Book 1\",\"Title__c\":\"Book 1 Title\",\"Author__c\":\"Author 1\"}";
        ObjectNode node = (ObjectNode) objectMapper.readTree(jsonStr);

        BookSObject book = mapper.toSObject(node, BookSObject.class);
        assertNotNull(book);
        assertEquals("Book 1 Title", book.getTitle());
        assertEquals("Author 1", book.getAuthor());
        assertEquals("Book 1", book.getName());
    }

    @Test
    void listMappers_withNullOrEmpty_returnsEmptyList() {
        assertTrue(mapper.toUserResponseList(null).isEmpty());
    }
}
