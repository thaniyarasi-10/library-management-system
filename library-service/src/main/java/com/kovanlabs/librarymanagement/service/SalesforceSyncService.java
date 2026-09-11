package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import com.kovanlabs.librarymanagement.user.service.SalesforceUserSyncDelegate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesforceSyncService implements SalesforceUserSyncDelegate {

    private final SalesforceClientService clientService;

    @Override
    public void syncUser(Object userObj) {
        if (userObj instanceof User u) {
            syncUser(u);
        }
    }

    // --- WRITE OPERATIONS ---

    public void syncUser(User user) {
        if (user == null || user.getUuid() == null)
            return;
        try {
            Map<String, Object> fields = new HashMap<>();
            String lastName = (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail();
            fields.put("LastName", lastName);
            fields.put("Email", user.getEmail());

            clientService.upsertByExternalId("Contact", "External_User_UUID__c", user.getUuid().toString(), fields);
        } catch (Exception e) {
            log.error("Salesforce sync error for User [UUID: {}]: {}", user.getUuid(), e.getMessage());
        }
    }

    public void syncBook(Book book) {
        if (book == null || book.getUuid() == null)
            return;
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put("Name", book.getTitle() != null ? book.getTitle() : "Untitled");
            fields.put("Title__c", book.getTitle());
            fields.put("Author__c", book.getAuthor());
            fields.put("ISBN__c", book.getIsbn());
            fields.put("Cover_Image_Url__c", book.getCoverImageUrl());
            clientService.upsertByExternalId("Book__c", "External_Book_UUID__c", book.getUuid().toString(), fields);
        } catch (Exception e) {
            log.error("Salesforce sync error for Book [UUID: {}]: {}", book.getUuid(), e.getMessage());
        }
    }

    public void syncBorrow(Borrow borrow) {
        if (borrow == null || borrow.getUuid() == null)
            return;
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put("External_Borrow_UUID__c", borrow.getUuid().toString());
            fields.put("Borrow_Date__c", borrow.getBorrowDate() != null ? borrow.getBorrowDate().toString() : null);
            fields.put("Due_Date__c", borrow.getDueDate() != null ? borrow.getDueDate().toString() : null);
            fields.put("Return_Date__c",
                    borrow.getReturnedDate() != null ? borrow.getReturnedDate().toString() : null);
            fields.put("Borrow_Status__c", borrow.getStatus() != null ? borrow.getStatus().name() : null);

            // Lookup relationship references via External ID
            if (borrow.getUser() != null && borrow.getUser().getUuid() != null) {
                Map<String, String> contactRef = Map.of("External_User_UUID__c", borrow.getUser().getUuid().toString());
                fields.put("Contact__r", contactRef);
            }
            if (borrow.getBook() != null && borrow.getBook().getUuid() != null) {
                Map<String, String> bookRef = Map.of("External_Book_UUID__c", borrow.getBook().getUuid().toString());
                fields.put("Book__r", bookRef);
            }

            clientService.upsertByExternalId("Borrow__c", "External_Borrow_UUID__c", borrow.getUuid().toString(), fields);
        } catch (Exception e) {
            log.error("Salesforce sync error for Borrow [UUID: {}]: {}", borrow.getUuid(), e.getMessage());
        }
    }

    // --- READ OPERATIONS (PRIMARY SOQL QUERIES) ---

    public List<UserResponse> fetchUsersFromSalesforce() {
        String soql = "SELECT LastName, Email, External_User_UUID__c FROM Contact WHERE External_User_UUID__c != null";
        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return null;
        }

        List<UserResponse> list = new ArrayList<>();
        for (JsonNode node : json.path("records")) {
            String name = node.path("LastName").asText(null);
            String email = node.path("Email").asText(null);
            String uuidStr = node.path("External_User_UUID__c").asText(null);
            UUID uuid = uuidStr != null ? parseUUID(uuidStr) : null;

            list.add(new UserResponse(uuid, null, name, email, 0));
        }
        return list;
    }

    public long getTotalBooksFromSalesforce() {
        String soql = "SELECT COUNT() FROM Book__c WHERE External_Book_UUID__c != null";
        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("totalSize")) {
            return 0;
        }
        return json.path("totalSize").asLong();
    }

    public List<BookResponse> fetchBooksFromSalesforce(int size, int offset) {
        String soql = String.format("SELECT Name, Title__c, Author__c, ISBN__c, Cover_Image_Url__c, External_Book_UUID__c FROM Book__c WHERE External_Book_UUID__c != null LIMIT %d OFFSET %d", size, offset);
        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return null;
        }

        List<BookResponse> list = new ArrayList<>();
        for (JsonNode node : json.path("records")) {
            String title = null;
            if (node.hasNonNull("Title__c") && !node.path("Title__c").asText().isBlank()) {
                title = node.path("Title__c").asText();
            } else {
                title = node.path("Name").asText(null);
            }

            String author = node.path("Author__c").asText(null);
            String isbn = node.path("ISBN__c").asText(null);
            String coverImageUrl = node.path("Cover_Image_Url__c").asText(null);
            String uuidStr = node.path("External_Book_UUID__c").asText(null);
            UUID uuid = uuidStr != null ? parseUUID(uuidStr) : null;

            list.add(new BookResponse(uuid, null, title, author, isbn, coverImageUrl));
        }
        return list;
    }

    public List<BorrowResponseDto> fetchBorrowsFromSalesforce() {
        String soql = "SELECT External_Borrow_UUID__c, Borrow_Date__c, Due_Date__c, Return_Date__c, Borrow_Status__c, "
                + "Contact__r.LastName, Contact__r.Email, Contact__r.External_User_UUID__c, "
                + "Book__r.Name, Book__r.Title__c, Book__r.Author__c, Book__r.Cover_Image_Url__c, Book__r.External_Book_UUID__c "
                + "FROM Borrow__c WHERE External_Borrow_UUID__c != null";
        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return null;
        }

        List<BorrowResponseDto> list = new ArrayList<>();
        for (JsonNode node : json.path("records")) {
            UUID borrowUuid = parseUUID(node.path("External_Borrow_UUID__c").asText(null));
            LocalDate borrowDate = parseDate(node.path("Borrow_Date__c").asText(null));
            LocalDate dueDate = parseDate(node.path("Due_Date__c").asText(null));
            LocalDate returnedDate = parseDate(node.path("Return_Date__c").asText(null));
            String statusStr = node.path("Borrow_Status__c").asText(null);
            BorrowStatus status = statusStr != null ? tryParseStatus(statusStr) : null;

            JsonNode contact = node.path("Contact__r");
            String userName = contact.path("LastName").asText(null);
            String userEmail = contact.path("Email").asText(null);
            UUID userUuid = parseUUID(contact.path("External_User_UUID__c").asText(null));

            JsonNode bookNode = node.path("Book__r");
            String bookTitle = null;
            if (bookNode.hasNonNull("Title__c") && !bookNode.path("Title__c").asText().isBlank()) {
                bookTitle = bookNode.path("Title__c").asText();
            }else {
                bookTitle = bookNode.path("Name").asText(null);
            }

            String bookAuthor = bookNode.path("Author__c").asText(null);
            String bookCoverUrl = bookNode.path("Cover_Image_Url__c").asText(null);
            UUID bookUuid = parseUUID(bookNode.path("External_Book_UUID__c").asText(null));

            BorrowResponseDto dto = BorrowResponseDto.builder()
                    .borrowUuid(borrowUuid)
                    .userId(userUuid)
                    .userName(userName)
                    .userEmail(userEmail)
                    .bookId(bookUuid)
                    .bookTitle(bookTitle)
                    .bookAuthor(bookAuthor)
                    .bookCoverImageUrl(bookCoverUrl)
                    .borrowDate(borrowDate)
                    .dueDate(dueDate)
                    .returnedDate(returnedDate)
                    .status(status)
                    .build();

            list.add(dto);
        }
        return list;
    }

    private UUID parseUUID(String str) {
        if (str == null || str.isBlank())
            return null;
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String str) {
        if (str == null || str.isBlank())
            return null;
        try {
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    private BorrowStatus tryParseStatus(String str) {
        try {
            return BorrowStatus.valueOf(str);
        } catch (Exception e) {
            return null;
        }
    }
}
