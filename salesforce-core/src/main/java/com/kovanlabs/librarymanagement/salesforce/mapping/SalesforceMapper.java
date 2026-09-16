package com.kovanlabs.librarymanagement.salesforce.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
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
        fields.put(ContactFields.LAST_NAME, lastName);
        fields.put(ContactFields.EMAIL, user.getEmail());
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
        fields.put(BookFields.NAME, displayName);
        fields.put(BookFields.TITLE, book.getTitle());
        fields.put(BookFields.AUTHOR, book.getAuthor());
        fields.put(BookFields.ISBN, book.getIsbn());
        fields.put(BookFields.COVER_IMAGE_URL, book.getCoverImageUrl());
        return fields;
    }

    public Map<String, Object> toBorrowFields(Borrow borrow) {
        if (borrow == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put(BorrowFields.BORROW_DATE,
                borrow.getBorrowDate() != null ? borrow.getBorrowDate().toString() : null);
        fields.put(BorrowFields.DUE_DATE, borrow.getDueDate() != null ? borrow.getDueDate().toString() : null);
        fields.put(BorrowFields.RETURN_DATE,
                borrow.getReturnedDate() != null ? borrow.getReturnedDate().toString() : null);
        fields.put(BorrowFields.BORROW_STATUS, borrow.getStatus() != null ? borrow.getStatus().name() : null);

        if (borrow.getUser() != null && borrow.getUser().getUuid() != null) {
            fields.put(BorrowFields.CONTACT_RELATION,
                    Map.of(ContactFields.EXTERNAL_USER_UUID, borrow.getUser().getUuid().toString()));
        }

        if (borrow.getBook() != null && borrow.getBook().getUuid() != null) {
            fields.put(BorrowFields.BOOK_RELATION,
                    Map.of(BookFields.EXTERNAL_BOOK_UUID, borrow.getBook().getUuid().toString()));
        }

        return fields;
    }

    // --- Salesforce SOQL JSON Node to UserResponse (Reads) ---

    public UserResponse toUserResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String uuidStr = node.path(ContactFields.EXTERNAL_USER_UUID).asText(null);
        UUID uuid = parseUUID(uuidStr);
        String name = node.path(ContactFields.LAST_NAME).asText(null);
        String email = node.path(ContactFields.EMAIL).asText(null);

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
