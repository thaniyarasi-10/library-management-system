package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.exception.SalesforceSyncException;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesforceSyncServiceTest {

    @Mock
    private SalesforceClientService clientService;

    @InjectMocks
    private SalesforceSyncImpl salesforceSyncService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // --- syncContact() tests ---

    @Test
    void syncContact_whenValidContact_shouldSync() {
        UUID uuid = UUID.randomUUID();
        ContactSObject contact = ContactSObject.builder()
                .externalUserUuid(uuid.toString())
                .legacyUserId(1L)
                .lastName("Jane Doe")
                .email("jane@example.com")
                .role("USER")
                .build();

        salesforceSyncService.syncContact(contact);

        verify(clientService).upsertByExternalId(eq(SObject.CONTACT.getObjectName()), eq(ContactFields.EXTERNAL_USER_UUID), eq(uuid.toString()), anyMap());
    }

    @Test
    void syncContact_whenContactOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncContact((ContactSObject) null);
        salesforceSyncService.syncContact(ContactSObject.builder().externalUserUuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncContact_whenClientThrowsException_propagatesException() {
        UUID uuid = UUID.randomUUID();
        ContactSObject contact = ContactSObject.builder()
                .externalUserUuid(uuid.toString())
                .legacyUserId(1L)
                .lastName("John")
                .email("john@example.com")
                .role("USER")
                .build();

        doThrow(new SalesforceSyncException("Salesforce connection error"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertThrows(SalesforceSyncException.class,
                () -> salesforceSyncService.syncContact(contact));
    }

    // --- deleteUser() tests ---

    @Test
    void deleteUser_whenValidUuid_shouldCallDelete() {
        String uuid = UUID.randomUUID().toString();

        salesforceSyncService.deleteUser(uuid);

        verify(clientService).deleteByExternalId(eq(SObject.CONTACT.getObjectName()), eq(ContactFields.EXTERNAL_USER_UUID), eq(uuid));
    }

    @Test
    void deleteUser_whenNullUuid_shouldReturnEarly() {
        salesforceSyncService.deleteUser(null);

        verifyNoInteractions(clientService);
    }

    @Test
    void syncBook_whenBookOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncBook(null);
        salesforceSyncService.syncBook(BookSObject.builder().externalBookUuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncBook_withValidBook_shouldCallUpsert() {
        UUID uuid = UUID.randomUUID();
        BookSObject book = BookSObject.builder()
                .externalBookUuid(uuid.toString())
                .name("Clean Code")
                .title("Clean Code")
                .author("Robert Martin")
                .isbn("1234567890")
                .coverImageUrl("http://images.com/cleancode.png")
                .build();

        salesforceSyncService.syncBook(book);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BOOK.getObjectName()), eq(BookFields.EXTERNAL_BOOK_UUID), eq(uuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals("Clean Code", fields.get(BookFields.NAME));
        assertEquals("Clean Code", fields.get(BookFields.TITLE));
        assertEquals("Robert Martin", fields.get(BookFields.AUTHOR));
        assertEquals("1234567890", fields.get(BookFields.ISBN));
        assertEquals("http://images.com/cleancode.png", fields.get(BookFields.COVER_IMAGE_URL));
    }

    @Test
    void syncBook_whenClientThrowsException_propagatesException() {
        BookSObject book = BookSObject.builder().externalBookUuid(UUID.randomUUID().toString()).title("Design Patterns").build();
        doThrow(new SalesforceSyncException("Upsert failed"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertThrows(SalesforceSyncException.class,
                () -> salesforceSyncService.syncBook(book));
    }

    // --- syncBorrow() tests ---

    @Test
    void syncBorrow_whenBorrowOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncBorrow(null);
        salesforceSyncService.syncBorrow(BorrowSObject.builder().externalBorrowUuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncBorrow_withFullReferences_shouldCallUpsert() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();

        BorrowSObject borrow = BorrowSObject.builder()
                .externalBorrowUuid(borrowUuid.toString())
                .borrowDate("2026-09-01")
                .dueDate("2026-09-15")
                .returnDate("2026-09-10")
                .borrowStatus("RETURNED")
                .contact(ContactSObject.builder().externalUserUuid(userUuid.toString()).lastName("Alice").email("alice@example.com").build())
                .book(BookSObject.builder().externalBookUuid(bookUuid.toString()).title("DDD").build())
                .build();

        salesforceSyncService.syncBorrow(borrow);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BORROW.getObjectName()), eq(BorrowFields.EXTERNAL_BORROW_UUID), eq(borrowUuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals("2026-09-01", fields.get(BorrowFields.BORROW_DATE));
        assertEquals("2026-09-15", fields.get(BorrowFields.DUE_DATE));
        assertEquals("2026-09-10", fields.get(BorrowFields.RETURN_DATE));
        assertEquals("RETURNED", fields.get(BorrowFields.BORROW_STATUS));
        assertNotNull(fields.get(BorrowFields.CONTACT_RELATION));
        assertNotNull(fields.get(BorrowFields.BOOK_RELATION));
    }

    @Test
    void syncBorrow_whenClientThrowsException_propagatesException() {
        BorrowSObject borrow = BorrowSObject.builder().externalBorrowUuid(UUID.randomUUID().toString()).build();
        doThrow(new SalesforceSyncException("Upsert failed"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertThrows(SalesforceSyncException.class,
                () -> salesforceSyncService.syncBorrow(borrow));
    }

    // --- fetchContactsFromSalesforce() tests ---

    @Test
    void fetchContactsFromSalesforce_whenQueryNullOrNoRecords_returnsEmptyList() {
        when(clientService.query(anyString())).thenReturn(null);
        List<ContactSObject> res1 = salesforceSyncService.fetchContactsFromSalesforce();
        assertNotNull(res1);
        assertTrue(res1.isEmpty());

        ObjectNode nodeWithoutRecords = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutRecords);
        List<ContactSObject> res2 = salesforceSyncService.fetchContactsFromSalesforce();
        assertNotNull(res2);
        assertTrue(res2.isEmpty());
    }

    @Test
    void fetchContactsFromSalesforce_withRecords_returnsMappedList() {
        UUID validUuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put(ContactFields.LAST_NAME, "Smith");
        record1.put(ContactFields.EMAIL, "smith@example.com");
        record1.put(ContactFields.EXTERNAL_USER_UUID, validUuid.toString());
        record1.put(ContactFields.LEGACY_USER_ID, "100");

        when(clientService.query(anyString())).thenReturn(root);

        List<ContactSObject> contacts = salesforceSyncService.fetchContactsFromSalesforce();

        assertNotNull(contacts);
        assertEquals(1, contacts.size());
        assertEquals(validUuid.toString(), contacts.get(0).getExternalUserUuid());
        assertEquals(100L, contacts.get(0).getLegacyUserId());
        assertEquals("Smith", contacts.get(0).getLastName());
    }

    // --- getTotalBooksFromSalesforce() tests ---

    @Test
    void getTotalBooksFromSalesforce_whenQueryNullOrNoTotalSize_returnsZero() {
        when(clientService.query(anyString())).thenReturn(null);
        assertEquals(0, salesforceSyncService.getTotalBooksFromSalesforce());

        ObjectNode nodeWithoutTotal = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutTotal);
        assertEquals(0, salesforceSyncService.getTotalBooksFromSalesforce());
    }

    @Test
    void getTotalBooksFromSalesforce_whenValid_returnsCount() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalSize", 150L);
        when(clientService.query(anyString())).thenReturn(root);

        assertEquals(150L, salesforceSyncService.getTotalBooksFromSalesforce());
    }

    // --- fetchBooksFromSalesforce() tests ---

    @Test
    void fetchBooksFromSalesforce_deserializesAndReturnsBookSObjectList() {
        UUID uuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put(BookFields.EXTERNAL_BOOK_UUID, uuid.toString());
        record1.put(BookFields.NAME, "Clean Code");
        record1.put(BookFields.TITLE, "Clean Code");
        record1.put(BookFields.AUTHOR, "Uncle Bob");

        when(clientService.query(anyString())).thenReturn(root);

        List<BookSObject> res = salesforceSyncService.fetchBooksFromSalesforce(10, 0);

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(uuid.toString(), res.get(0).getExternalBookUuid());
        assertEquals("Clean Code", res.get(0).getTitle());
        assertEquals("Uncle Bob", res.get(0).getAuthor());
    }

    // --- fetchBorrowsFromSalesforce() tests ---

    @Test
    void fetchBorrowsFromSalesforce_deserializesAndReturnsBorrowSObjectList() {
        UUID uuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put(BorrowFields.EXTERNAL_BORROW_UUID, uuid.toString());
        record1.put(BorrowFields.BORROW_DATE, "2026-09-01");
        record1.put(BorrowFields.BORROW_STATUS, "BORROWED");

        when(clientService.query(anyString())).thenReturn(root);

        List<BorrowSObject> res = salesforceSyncService.fetchBorrowsFromSalesforce();

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(uuid.toString(), res.get(0).getExternalBorrowUuid());
        assertEquals("BORROWED", res.get(0).getBorrowStatus());
    }

    @Test
    void fetchBooksFromSalesforce_withErrorsInResponse_logsAndReturnsBooks() {
        UUID uuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put(BookFields.EXTERNAL_BOOK_UUID, uuid.toString());
        record1.put(BookFields.TITLE, "Faulty Book");
        ArrayNode errs = record1.putArray("errors");
        errs.add("Salesforce partial failure");

        when(clientService.query(anyString())).thenReturn(root);

        List<BookSObject> res = salesforceSyncService.fetchBooksFromSalesforce(10, 0);

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).getErrors().size());
        assertEquals("Salesforce partial failure", res.get(0).getErrors().get(0));
    }
}
