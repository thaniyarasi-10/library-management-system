package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

        verify(clientService).upsertByExternalId(eq("Contact"), eq("External_User_UUID__c"), eq(user.getUuid().toString()), anyMap());
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
        verify(clientService, times(2)).upsertByExternalId(eq("Contact"), eq("External_User_UUID__c"), eq(uuid.toString()), captor.capture());

        List<Map<String, Object>> capturedMaps = captor.getAllValues();
        assertEquals("nullname@example.com", capturedMaps.get(0).get("LastName"));
        assertEquals("blankname@example.com", capturedMaps.get(1).get("LastName"));
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
        verify(clientService).upsertByExternalId(eq("Book__c"), eq("External_Book_UUID__c"), eq(uuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals("Clean Code", fields.get("Name"));
        assertEquals("Clean Code", fields.get("Title__c"));
        assertEquals("Robert Martin", fields.get("Author__c"));
        assertEquals("1234567890", fields.get("ISBN__c"));
        assertEquals("http://images.com/cleancode.png", fields.get("Cover_Image_Url__c"));
    }

    @Test
    void syncBook_whenTitleNull_usesUntitled() {
        UUID uuid = UUID.randomUUID();
        Book book = Book.builder().uuid(uuid).title(null).build();

        salesforceSyncService.syncBook(book);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(clientService).upsertByExternalId(eq("Book__c"), eq("External_Book_UUID__c"), eq(uuid.toString()), captor.capture());

        assertEquals("Untitled", captor.getValue().get("Name"));
        assertNull(captor.getValue().get("Title__c"));
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
        verify(clientService).upsertByExternalId(eq("Borrow__c"), eq("External_Borrow_UUID__c"), eq(borrowUuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertEquals(borrowUuid.toString(), fields.get("External_Borrow_UUID__c"));
        assertEquals(now.toString(), fields.get("Borrow_Date__c"));
        assertEquals(now.plusDays(14).toString(), fields.get("Due_Date__c"));
        assertEquals(now.plusDays(10).toString(), fields.get("Return_Date__c"));
        assertEquals("RETURNED", fields.get("Borrow_Status__c"));
        assertEquals(Map.of("External_User_UUID__c", userUuid.toString()), fields.get("Contact__r"));
        assertEquals(Map.of("External_Book_UUID__c", bookUuid.toString()), fields.get("Book__r"));
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
        verify(clientService).upsertByExternalId(eq("Borrow__c"), eq("External_Borrow_UUID__c"), eq(borrowUuid.toString()), captor.capture());

        Map<String, Object> fields = captor.getValue();
        assertNull(fields.get("Borrow_Date__c"));
        assertNull(fields.get("Due_Date__c"));
        assertNull(fields.get("Return_Date__c"));
        assertNull(fields.get("Borrow_Status__c"));
        assertFalse(fields.containsKey("Contact__r"));
        assertFalse(fields.containsKey("Book__r"));
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
    void fetchUsersFromSalesforce_whenQueryNullOrNoRecords_returnsNull() {
        when(clientService.query(anyString())).thenReturn(null);
        assertNull(salesforceSyncService.fetchUsersFromSalesforce());

        ObjectNode nodeWithoutRecords = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutRecords);
        assertNull(salesforceSyncService.fetchUsersFromSalesforce());
    }

    @Test
    void fetchUsersFromSalesforce_withRecordsAndInvalidUuid_returnsMappedList() {
        UUID validUuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        ObjectNode record1 = records.addObject();
        record1.put("LastName", "Smith");
        record1.put("Email", "smith@example.com");
        record1.put("External_User_UUID__c", validUuid.toString());

        ObjectNode record2 = records.addObject();
        record2.put("LastName", "Unknown");
        record2.put("Email", "unknown@example.com");
        record2.put("External_User_UUID__c", "not-a-valid-uuid");

        ObjectNode record3 = records.addObject();
        record3.put("LastName", "NullUuid");
        record3.put("Email", "nulluuid@example.com");
        record3.putNull("External_User_UUID__c");

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

    // --- fetchBooksFromSalesforce() tests ---

    @Test
    void fetchBooksFromSalesforce_whenQueryNullOrNoRecords_returnsNull() {
        when(clientService.query(anyString())).thenReturn(null);
        assertNull(salesforceSyncService.fetchBooksFromSalesforce(10, 0));

        ObjectNode nodeWithoutRecords = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutRecords);
        assertNull(salesforceSyncService.fetchBooksFromSalesforce(10, 0));
    }

    @Test
    void fetchBooksFromSalesforce_withTitleFieldFallbacks_returnsBookList() {
        UUID bookUuid = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        // Case 1: Title__c present
        ObjectNode book1 = records.addObject();
        book1.put("Name", "BK-001");
        book1.put("Title__c", "Spring in Action");
        book1.put("Author__c", "Craig Walls");
        book1.put("ISBN__c", "1617294942");
        book1.put("Cover_Image_Url__c", "http://cover.jpg");
        book1.put("External_Book_UUID__c", bookUuid.toString());

        // Case 2: Title__c blank, fallback to Name
        ObjectNode book2 = records.addObject();
        book2.put("Name", "Fallback Name Title");
        book2.put("Title__c", "   ");
        book2.putNull("External_Book_UUID__c");

        when(clientService.query(anyString())).thenReturn(root);

        List<BookResponse> books = salesforceSyncService.fetchBooksFromSalesforce(10, 0);

        assertNotNull(books);
        assertEquals(2, books.size());
        assertEquals("Spring in Action", books.get(0).title());
        assertEquals(bookUuid, books.get(0).uuid());
        assertEquals("Fallback Name Title", books.get(1).title());
        assertNull(books.get(1).uuid());
    }

    // --- fetchBorrowsFromSalesforce() tests ---

    @Test
    void fetchBorrowsFromSalesforce_whenQueryNullOrNoRecords_returnsNull() {
        when(clientService.query(anyString())).thenReturn(null);
        assertNull(salesforceSyncService.fetchBorrowsFromSalesforce());

        ObjectNode nodeWithoutRecords = objectMapper.createObjectNode();
        when(clientService.query(anyString())).thenReturn(nodeWithoutRecords);
        assertNull(salesforceSyncService.fetchBorrowsFromSalesforce());
    }

    @Test
    void fetchBorrowsFromSalesforce_withFullAndPartialData_returnsBorrowList() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");

        // Record 1: Complete and valid
        ObjectNode rec1 = records.addObject();
        rec1.put("External_Borrow_UUID__c", borrowUuid.toString());
        rec1.put("Borrow_Date__c", "2026-09-01");
        rec1.put("Due_Date__c", "2026-09-15");
        rec1.put("Return_Date__c", "2026-09-10");
        rec1.put("Borrow_Status__c", "RETURNED");

        ObjectNode contact1 = rec1.putObject("Contact__r");
        contact1.put("LastName", "Alice");
        contact1.put("Email", "alice@example.com");
        contact1.put("External_User_UUID__c", userUuid.toString());

        ObjectNode bookNode1 = rec1.putObject("Book__r");
        bookNode1.put("Title__c", "Microservices Patterns");
        bookNode1.put("Author__c", "Chris Richardson");
        bookNode1.put("Cover_Image_Url__c", "http://book.jpg");
        bookNode1.put("External_Book_UUID__c", bookUuid.toString());

        // Record 2: Invalid status, invalid dates, book Title fallback to Name
        ObjectNode rec2 = records.addObject();
        rec2.put("External_Borrow_UUID__c", "invalid-uuid");
        rec2.put("Borrow_Date__c", "not-a-date");
        rec2.put("Due_Date__c", "");
        rec2.putNull("Return_Date__c");
        rec2.put("Borrow_Status__c", "INVALID_ENUM_STATUS");

        ObjectNode bookNode2 = rec2.putObject("Book__r");
        bookNode2.put("Name", "Name Based Title");
        bookNode2.putNull("Title__c");

        when(clientService.query(anyString())).thenReturn(root);

        List<BorrowResponseDto> borrows = salesforceSyncService.fetchBorrowsFromSalesforce();

        assertNotNull(borrows);
        assertEquals(2, borrows.size());

        BorrowResponseDto b1 = borrows.get(0);
        assertEquals(borrowUuid, b1.borrowUuid());
        assertEquals(userUuid, b1.userId());
        assertEquals("Alice", b1.userName());
        assertEquals("alice@example.com", b1.userEmail());
        assertEquals(bookUuid, b1.bookId());
        assertEquals("Microservices Patterns", b1.bookTitle());
        assertEquals(LocalDate.of(2026, 9, 1), b1.borrowDate());
        assertEquals(LocalDate.of(2026, 9, 15), b1.dueDate());
        assertEquals(LocalDate.of(2026, 9, 10), b1.returnedDate());
        assertEquals(BorrowStatus.RETURNED, b1.status());

        BorrowResponseDto b2 = borrows.get(1);
        assertNull(b2.borrowUuid());
        assertNull(b2.borrowDate());
        assertNull(b2.dueDate());
        assertNull(b2.returnedDate());
        assertNull(b2.status());
        assertEquals("Name Based Title", b2.bookTitle());
    }
}
