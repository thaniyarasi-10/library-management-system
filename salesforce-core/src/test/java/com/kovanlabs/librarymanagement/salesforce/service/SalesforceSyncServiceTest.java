package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.salesforce.constant.BookFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.BorrowFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.ContactFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.mapping.SalesforceMapper;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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

    @Spy
    private SalesforceMapper salesforceMapper = new SalesforceMapper();

    @InjectMocks
    private SalesforceSyncService salesforceSyncService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // --- syncUser() tests ---

    @Test
    void syncUser_viaObjectDelegate_whenUserInstance_shouldSync() {
        User user = User.builder().uuid(UUID.randomUUID()).name("Jane Doe").email("jane@example.com").build();

        salesforceSyncService.syncUser((Object) user);

        verify(clientService).upsertByExternalId(eq(SObject.CONTACT.getObjectName()), eq(ContactFieldConstants.EXTERNAL_USER_UUID), eq(user.getUuid().toString()), anyMap());
    }

    @Test
    void syncUser_viaObjectDelegate_whenNotUserInstance_shouldDoNothing() {
        salesforceSyncService.syncUser("NotAUserObject");

        verifyNoInteractions(clientService);
    }

    @Test
    void syncUser_whenUserOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncUser((User) null);
        salesforceSyncService.syncUser(User.builder().uuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncUser_whenNameBlankOrNull_usesEmailAsLastName() {
        UUID uuid = UUID.randomUUID();
        User userWithNullName = User.builder().uuid(uuid).name(null).email("nullname@example.com").build();
        User userWithBlankName = User.builder().uuid(uuid).name("   ").email("blankname@example.com").build();

        salesforceSyncService.syncUser(userWithNullName);
        salesforceSyncService.syncUser(userWithBlankName);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService, times(2)).upsertByExternalId(eq(SObject.CONTACT.getObjectName()), eq(ContactFieldConstants.EXTERNAL_USER_UUID), eq(uuid.toString()), captor.capture());

        List<Map<String, Object>> capturedMaps = captor.getAllValues();
        assertEquals("nullname@example.com", capturedMaps.get(0).get(ContactFieldConstants.LAST_NAME));
        assertEquals("blankname@example.com", capturedMaps.get(1).get(ContactFieldConstants.LAST_NAME));
    }

    @Test
    void syncUser_whenClientThrowsException_handlesGracefully() {
        User user = User.builder().uuid(UUID.randomUUID()).name("John").email("john@example.com").build();
        doThrow(new RuntimeException("Salesforce connection error"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertDoesNotThrow(() -> salesforceSyncService.syncUser(user));
    }

    // --- syncBook() tests ---

    @Test
    void syncBook_whenBookOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncBook(null);
        salesforceSyncService.syncBook(Book.builder().uuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncBook_withValidBook_shouldCallUpsert() {
        UUID uuid = UUID.randomUUID();
        Book book = Book.builder()
                .uuid(uuid)
                .title("Clean Code")
                .author("Robert Martin")
                .isbn("1234567890")
                .coverImageUrl("http://images.com/cleancode.png")
                .build();

        salesforceSyncService.syncBook(book);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BOOK.getObjectName()), eq(BookFieldConstants.EXTERNAL_BOOK_UUID), eq(uuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals("Clean Code", fields.get(BookFieldConstants.NAME));
        assertEquals("Clean Code", fields.get(BookFieldConstants.TITLE));
        assertEquals("Robert Martin", fields.get(BookFieldConstants.AUTHOR));
        assertEquals("1234567890", fields.get(BookFieldConstants.ISBN));
        assertEquals("http://images.com/cleancode.png", fields.get(BookFieldConstants.COVER_IMAGE_URL));
    }

    @Test
    void syncBook_whenTitleNull_usesUntitled() {
        UUID uuid = UUID.randomUUID();
        Book book = Book.builder().uuid(uuid).title(null).build();

        salesforceSyncService.syncBook(book);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BOOK.getObjectName()), eq(BookFieldConstants.EXTERNAL_BOOK_UUID), eq(uuid.toString()), captor.capture());

        assertEquals("Untitled", captor.getValue().get(BookFieldConstants.NAME));
        assertNull(captor.getValue().get(BookFieldConstants.TITLE));
    }

    @Test
    void syncBook_whenClientThrowsException_handlesGracefully() {
        Book book = Book.builder().uuid(UUID.randomUUID()).title("Design Patterns").build();
        doThrow(new RuntimeException("Upsert failed"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertDoesNotThrow(() -> salesforceSyncService.syncBook(book));
    }

    // --- syncBorrow() tests ---

    @Test
    void syncBorrow_whenBorrowOrUuidNull_shouldReturnEarly() {
        salesforceSyncService.syncBorrow(null);
        salesforceSyncService.syncBorrow(Borrow.builder().uuid(null).build());

        verifyNoInteractions(clientService);
    }

    @Test
    void syncBorrow_withFullReferences_shouldCallUpsert() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        User user = User.builder().uuid(userUuid).build();
        Book book = Book.builder().uuid(bookUuid).build();
        Borrow borrow = Borrow.builder()
                .uuid(borrowUuid)
                .user(user)
                .book(book)
                .borrowDate(now)
                .dueDate(now.plusDays(14))
                .returnedDate(now.plusDays(10))
                .status(BorrowStatus.RETURNED)
                .build();

        salesforceSyncService.syncBorrow(borrow);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BORROW.getObjectName()), eq(BorrowFieldConstants.EXTERNAL_BORROW_UUID), eq(borrowUuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals(borrowUuid.toString(), fields.get(BorrowFieldConstants.EXTERNAL_BORROW_UUID));
        assertEquals(now.toString(), fields.get(BorrowFieldConstants.BORROW_DATE));
        assertEquals(now.plusDays(14).toString(), fields.get(BorrowFieldConstants.DUE_DATE));
        assertEquals(now.plusDays(10).toString(), fields.get(BorrowFieldConstants.RETURN_DATE));
        assertEquals("RETURNED", fields.get(BorrowFieldConstants.BORROW_STATUS));
        assertEquals(Map.of(ContactFieldConstants.EXTERNAL_USER_UUID, userUuid.toString()), fields.get(BorrowFieldConstants.CONTACT_RELATION));
        assertEquals(Map.of(BookFieldConstants.EXTERNAL_BOOK_UUID, bookUuid.toString()), fields.get(BorrowFieldConstants.BOOK_RELATION));
    }

    @Test
    void syncBorrow_withNullRelationsAndDates_shouldHandleSafely() {
        UUID borrowUuid = UUID.randomUUID();
        Borrow borrow = Borrow.builder()
                .uuid(borrowUuid)
                .user(null)
                .book(null)
                .borrowDate(null)
                .dueDate(null)
                .returnedDate(null)
                .status(null)
                .build();

        salesforceSyncService.syncBorrow(borrow);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq(SObject.BORROW.getObjectName()), eq(BorrowFieldConstants.EXTERNAL_BORROW_UUID), eq(borrowUuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertNull(fields.get(BorrowFieldConstants.BORROW_DATE));
        assertNull(fields.get(BorrowFieldConstants.DUE_DATE));
        assertNull(fields.get(BorrowFieldConstants.RETURN_DATE));
        assertNull(fields.get(BorrowFieldConstants.BORROW_STATUS));
        assertFalse(fields.containsKey(BorrowFieldConstants.CONTACT_RELATION));
        assertFalse(fields.containsKey(BorrowFieldConstants.BOOK_RELATION));
    }

    @Test
    void syncBorrow_whenClientThrowsException_handlesGracefully() {
        Borrow borrow = Borrow.builder().uuid(UUID.randomUUID()).build();
        doThrow(new RuntimeException("Upsert failed"))
                .when(clientService).upsertByExternalId(anyString(), anyString(), anyString(), anyMap());

        assertDoesNotThrow(() -> salesforceSyncService.syncBorrow(borrow));
    }

    // --- fetchUsersFromSalesforce() tests ---

    @Test
    void fetchUsersFromSalesforce_whenQueryNullOrNoRecords_returnsEmptyList() {
        when(clientService.query(anyString())).thenReturn(null);
        List<UserResponse> res1 = salesforceSyncService.fetchUsersFromSalesforce();
        assertNotNull(res1);
        assertTrue(res1.isEmpty());

        ObjectNode nodeWithoutRecords = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutRecords);
        List<UserResponse> res2 = salesforceSyncService.fetchUsersFromSalesforce();
        assertNotNull(res2);
        assertTrue(res2.isEmpty());
    }

    @Test
    void fetchUsersFromSalesforce_withRecordsAndInvalidUuid_returnsMappedList() {
        UUID validUuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put(ContactFieldConstants.LAST_NAME, "Smith");
        record1.put(ContactFieldConstants.EMAIL, "smith@example.com");
        record1.put(ContactFieldConstants.EXTERNAL_USER_UUID, validUuid.toString());

        ObjectNode record2 = records.addObject();
        record2.put(ContactFieldConstants.LAST_NAME, "Unknown");
        record2.put(ContactFieldConstants.EMAIL, "unknown@example.com");
        record2.put(ContactFieldConstants.EXTERNAL_USER_UUID, "not-a-valid-uuid");

        ObjectNode record3 = records.addObject();
        record3.put(ContactFieldConstants.LAST_NAME, "NullUuid");
        record3.put(ContactFieldConstants.EMAIL, "nulluuid@example.com");
        record3.putNull(ContactFieldConstants.EXTERNAL_USER_UUID);

        when(clientService.query(anyString())).thenReturn(root);

        List<UserResponse> users = salesforceSyncService.fetchUsersFromSalesforce();

        assertNotNull(users);
        assertEquals(3, users.size());
        assertEquals(validUuid, users.get(0).uuid());
        assertEquals("Smith", users.get(0).name());
        assertNull(users.get(1).uuid());
        assertNull(users.get(2).uuid());
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

    // --- fetchBooksJsonFromSalesforce() tests ---

    @Test
    void fetchBooksJsonFromSalesforce_delegatesToClientService() {
        ObjectNode root = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(root);

        JsonNode res = salesforceSyncService.fetchBooksJsonFromSalesforce(10, 0);

        assertSame(root, res);
        verify(clientService).query(contains("FROM Book__c"));
    }

    // --- fetchBorrowsJsonFromSalesforce() tests ---

    @Test
    void fetchBorrowsJsonFromSalesforce_delegatesToClientService() {
        ObjectNode root = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(root);

        JsonNode res = salesforceSyncService.fetchBorrowsJsonFromSalesforce();

        assertSame(root, res);
        verify(clientService).query(contains("FROM Borrow__c"));
    }
}
