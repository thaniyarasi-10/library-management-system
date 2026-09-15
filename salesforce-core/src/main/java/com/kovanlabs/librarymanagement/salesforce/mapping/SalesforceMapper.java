package com.kovanlabs.librarymanagement.salesforce.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.salesforce.constant.BookFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.BorrowFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.ContactFieldConstants;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SalesforceMapper {

    // --- Entity to Salesforce Payload Map (Writes) ---

    public Map<String, Object> toContactFields(User user) {
        if (user == null) {
            return Collections.emptyMap();
        }
        String lastName = (user.getName() != null && !user.getName().isBlank())
                ? user.getName()
                : user.getEmail();

        Map<String, Object> fields = new HashMap<>();
        fields.put(ContactFieldConstants.LAST_NAME, lastName);
        fields.put(ContactFieldConstants.EMAIL, user.getEmail());
        return fields;
    }

    public Map<String, Object> toBookFields(Book book) {
        if (book == null) {
            return Collections.emptyMap();
        }
        String displayName = (book.getTitle() != null && !book.getTitle().isBlank())
                ? book.getTitle()
                : "Untitled";

        Map<String, Object> fields = new HashMap<>();
        fields.put(BookFieldConstants.NAME, displayName);
        fields.put(BookFieldConstants.TITLE, book.getTitle());
        fields.put(BookFieldConstants.AUTHOR, book.getAuthor());
        fields.put(BookFieldConstants.ISBN, book.getIsbn());
        fields.put(BookFieldConstants.COVER_IMAGE_URL, book.getCoverImageUrl());
        return fields;
    }

    public Map<String, Object> toBorrowFields(Borrow borrow) {
        if (borrow == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put(BorrowFieldConstants.BORROW_DATE,
                borrow.getBorrowDate() != null ? borrow.getBorrowDate().toString() : null);
        fields.put(BorrowFieldConstants.DUE_DATE, borrow.getDueDate() != null ? borrow.getDueDate().toString() : null);
        fields.put(BorrowFieldConstants.RETURN_DATE,
                borrow.getReturnedDate() != null ? borrow.getReturnedDate().toString() : null);
        fields.put(BorrowFieldConstants.BORROW_STATUS, borrow.getStatus() != null ? borrow.getStatus().name() : null);

        if (borrow.getUser() != null && borrow.getUser().getUuid() != null) {
            fields.put(BorrowFieldConstants.CONTACT_RELATION,
                    Map.of(ContactFieldConstants.EXTERNAL_USER_UUID, borrow.getUser().getUuid().toString()));
        }

        if (borrow.getBook() != null && borrow.getBook().getUuid() != null) {
            fields.put(BorrowFieldConstants.BOOK_RELATION,
                    Map.of(BookFieldConstants.EXTERNAL_BOOK_UUID, borrow.getBook().getUuid().toString()));
        }

        return fields;
    }

    // --- Salesforce SOQL JSON Node to UserResponse (Reads) ---

    public UserResponse toUserResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String uuidStr = node.path(ContactFieldConstants.EXTERNAL_USER_UUID).asText(null);
        UUID uuid = parseUUID(uuidStr);
        String name = node.path(ContactFieldConstants.LAST_NAME).asText(null);
        String email = node.path(ContactFieldConstants.EMAIL).asText(null);

        return new UserResponse(uuid, null, name, email, 0);
    }

    public List<UserResponse> toUserResponseList(List<JsonNode> nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        return nodes.stream()
                .map(this::toUserResponse)
                .toList();
    }

    private static UUID parseUUID(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }
}
