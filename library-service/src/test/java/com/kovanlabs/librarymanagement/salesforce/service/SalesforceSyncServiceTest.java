package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
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

    @Test
    void syncUser_shouldCallUpsert() {
        User user = User.builder().uuid(UUID.randomUUID()).name("John Doe").email("john@example.com").build();

        salesforceSyncService.syncUser(user);

        verify(clientService).upsertByExternalId(eq("Contact"), eq("External_User_UUID__c"), eq(user.getUuid().toString()), anyMap());
    }

    @Test
    void syncBook_shouldCallUpsert() {
        Book book = Book.builder().uuid(UUID.randomUUID()).title("Clean Code").author("Robert Martin").isbn("123456").coverImageUrl("http://cover.png").build();

        salesforceSyncService.syncBook(book);

        verify(clientService).upsertByExternalId(eq("Book__c"), eq("External_Book_UUID__c"), eq(book.getUuid().toString()), anyMap());
    }

    @Test
    void syncBorrow_shouldCallUpsert() {
        User user = User.builder().uuid(UUID.randomUUID()).build();
        Book book = Book.builder().uuid(UUID.randomUUID()).build();
        Borrow borrow = Borrow.builder()
                .uuid(UUID.randomUUID())
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();

        salesforceSyncService.syncBorrow(borrow);

        verify(clientService).upsertByExternalId(eq("Borrow__c"), eq("External_Borrow_UUID__c"), eq(borrow.getUuid().toString()), anyMap());
    }

    @Test
    void fetchUsersFromSalesforce_shouldReturnUserResponses() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");
        ObjectNode record = records.addObject();
        record.put("LastName", "Doe");
        record.put("Email", "john@example.com");
        record.put("External_User_UUID__c", UUID.randomUUID().toString());

        when(clientService.query(anyString())).thenReturn(root);

        List<UserResponse> users = salesforceSyncService.fetchUsersFromSalesforce();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("john@example.com", users.get(0).email());
    }

    @Test
    void getTotalBooksFromSalesforce_shouldReturnTotalSize() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalSize", 42L);

        when(clientService.query(anyString())).thenReturn(root);

        long total = salesforceSyncService.getTotalBooksFromSalesforce();

        assertEquals(42L, total);
    }

    @Test
    void fetchBooksFromSalesforce_shouldReturnBookResponses() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");
        ObjectNode record = records.addObject();
        record.put("Title__c", "Effective Java");
        record.put("Author__c", "Joshua Bloch");
        record.put("ISBN__c", "9780134685991");
        record.put("External_Book_UUID__c", UUID.randomUUID().toString());

        when(clientService.query(anyString())).thenReturn(root);

        List<BookResponse> books = salesforceSyncService.fetchBooksFromSalesforce(10, 0);

        assertNotNull(books);
        assertEquals(1, books.size());
        assertEquals("Effective Java", books.get(0).title());
    }

    @Test
    void fetchBorrowsFromSalesforce_shouldReturnBorrowResponses() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode records = root.putArray("records");
        ObjectNode record = records.addObject();
        record.put("External_Borrow_UUID__c", UUID.randomUUID().toString());
        record.put("Borrow_Date__c", "2026-09-01");
        record.put("Borrow_Status__c", "BORROWED");

        ObjectNode contact = record.putObject("Contact__r");
        contact.put("LastName", "Doe");
        contact.put("Email", "john@example.com");

        ObjectNode bookNode = record.putObject("Book__r");
        bookNode.put("Title__c", "Clean Code");

        when(clientService.query(anyString())).thenReturn(root);

        List<BorrowResponseDto> borrows = salesforceSyncService.fetchBorrowsFromSalesforce();

        assertNotNull(borrows);
        assertEquals(1, borrows.size());
        assertEquals("Clean Code", borrows.get(0).bookTitle());
    }
}
