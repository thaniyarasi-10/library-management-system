package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.salesforce.builder.SOQLBuilder;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for synchronizing User, Book, and Borrow entities with Salesforce SObjects.
 * <p>
 * Implements {@link SalesforceSync} to integrate with user module workflows,
 * and provides methods to push and pull SObjects to/from Salesforce via SOQL queries and REST APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesforceSyncImpl implements SalesforceSync {

    private final SalesforceClientService clientService;

    // --- WRITE OPERATIONS (SObject -> Map payload -> Salesforce) ---

    /**
     * Synchronizes a {@link ContactSObject} to Salesforce via upsert on External ID.
     *
     * @param contact The Contact SObject to upsert
     */
    @Override
    public void syncContact(ContactSObject contact) {
        if (Objects.isNull(contact) || Objects.isNull(contact.getExternalUserUuid())) {
            return;
        }
        Map<String, Object> fields = toPayloadMap(contact);
        fields.remove(ContactSObject.EXTERNAL_ID_FIELD);
        clientService.upsertByExternalId(
                SObject.CONTACT.getObjectName(),
                ContactSObject.EXTERNAL_ID_FIELD,
                contact.getExternalUserUuid(),
                fields
        );
    }

    /**
     * Deletes a User record from Salesforce Contact by external UUID.
     *
     * @param userUuid The user UUID
     */
    @Override
    public void deleteUser(String userUuid) {
        if (Objects.isNull(userUuid)) {
            return;
        }
        clientService.deleteByExternalId(
                SObject.CONTACT.getObjectName(),
                ContactSObject.EXTERNAL_ID_FIELD,
                userUuid
        );
    }

    /**
     * Synchronizes a {@link BookSObject} to Salesforce via upsert on External ID.
     *
     * @param book The Book SObject to upsert
     */
    @Override
    public void syncBook(BookSObject book) {
        if (Objects.isNull(book) || Objects.isNull(book.getExternalBookUuid())) {
            return;
        }
        Map<String, Object> fields = toPayloadMap(book);
        fields.remove(BookSObject.EXTERNAL_ID_FIELD);
        clientService.upsertByExternalId(
                SObject.BOOK.getObjectName(),
                BookSObject.EXTERNAL_ID_FIELD,
                book.getExternalBookUuid(),
                fields
        );
    }

    /**
     * Synchronizes a {@link BorrowSObject} to Salesforce via upsert on External ID.
     *
     * @param borrow The Borrow SObject to upsert
     */
    @Override
    public void syncBorrow(BorrowSObject borrow) {
        if (Objects.isNull(borrow) || Objects.isNull(borrow.getExternalBorrowUuid())) {
            return;
        }

        Map<String, Object> fields = toPayloadMap(borrow);

        fields.remove(BorrowSObject.EXTERNAL_ID_FIELD);

        if (Objects.nonNull(borrow.getBook())) {
            Map<String, Object> bookReference = Map.of(
                    BookSObject.EXTERNAL_ID_FIELD,
                    borrow.getBook().getExternalBookUuid()
            );

            fields.put(BorrowFields.BOOK_RELATION, bookReference);
        }

        if (Objects.nonNull(borrow.getContact())) {
            Map<String, Object> contactReference = Map.of(
                    ContactSObject.EXTERNAL_ID_FIELD,
                    borrow.getContact().getExternalUserUuid()
            );

            fields.put(BorrowFields.CONTACT_RELATION, contactReference);
        }

        clientService.upsertByExternalId(
                SObject.BORROW.getObjectName(),
                BorrowSObject.EXTERNAL_ID_FIELD,
                borrow.getExternalBorrowUuid(),
                fields
        );
    }

    // --- READ OPERATIONS (SOQL -> SObjects) ---

    /**
     * Queries and fetches all {@link ContactSObject} records from Salesforce that have an external UUID.
     *
     * @return List of {@link ContactSObject} instances
     */
    @Override
    public List<ContactSObject> fetchContactsFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(ContactFields.EXTERNAL_USER_UUID, ContactFields.LEGACY_USER_ID, ContactFields.LAST_NAME, ContactFields.EMAIL, ContactFields.ROLE, ContactFields.PHONE)
                .from(SObject.CONTACT)
                .whereNotNull(ContactSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (Objects.isNull(json) || !json.has("records")) {
            return Collections.emptyList();
        }

        List<ContactSObject> contacts = toSObjectList(json, ContactSObject.class);
        contacts.stream()
                .filter(contact -> Objects.nonNull(contact) && Objects.nonNull(contact.getErrors()) && !contact.getErrors().isEmpty())
                .forEach(contact -> log.warn("Salesforce error for Contact [UUID: {}]: {}", contact.getExternalUserUuid(), contact.getErrors()));
        return contacts;
    }

    /**
     * Retrieves the total count of {@link BookSObject} records currently present in Salesforce.
     *
     * @return The total count of book records in Salesforce
     */
    @Override
    public long getTotalBooksFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .count()
                .from(SObject.BOOK)
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        return (Objects.isNull(json) || !json.has("totalSize")) ? 0 : json.path("totalSize").asLong();
    }

    /**
     * Fetches a paginated list of {@link BookSObject} records from Salesforce.
     *
     * @param size Number of records to return (LIMIT)
     * @param offset Number of records to skip (OFFSET)
     * @return List of {@link BookSObject} instances
     */
    @Override
    public List<BookSObject> fetchBooksFromSalesforce(int size, int offset) {
        String soql = new SOQLBuilder<>()
                .select(BookFields.EXTERNAL_BOOK_UUID, BookFields.NAME, BookFields.TITLE, BookFields.AUTHOR, BookFields.ISBN, BookFields.COVER_IMAGE_URL, BookFields.BOOK_COUNT)
                .from(SObject.BOOK)
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .limit(size)
                .offset(offset)
                .build();

        JsonNode json = clientService.query(soql);
        if (Objects.isNull(json) || !json.has("records")) {
            return Collections.emptyList();
        }

        List<BookSObject> books = toSObjectList(json, BookSObject.class);
        books.stream()
                .filter(book -> Objects.nonNull(book) && Objects.nonNull(book.getErrors()) && !book.getErrors().isEmpty())
                .forEach(book -> log.warn("Salesforce error for Book [UUID: {}]: {}", book.getExternalBookUuid(), book.getErrors()));
        return books;
    }

    /**
     * Queries and fetches all {@link BorrowSObject} records from Salesforce that have an external UUID.
     *
     * @return List of {@link BorrowSObject} instances
     */
    @Override
    public List<BorrowSObject> fetchBorrowsFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(BorrowFields.EXTERNAL_BORROW_UUID, BorrowFields.BORROW_DATE, BorrowFields.DUE_DATE, BorrowFields.RETURN_DATE, BorrowFields.BORROW_STATUS)
                .from(SObject.BORROW)
                .whereNotNull(BorrowSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (Objects.isNull(json) || !json.has("records")) {
            return Collections.emptyList();
        }

        List<BorrowSObject> borrows = toSObjectList(json, BorrowSObject.class);
        borrows.stream()
                .filter(borrow -> Objects.nonNull(borrow) && Objects.nonNull(borrow.getErrors()) && !borrow.getErrors().isEmpty())
                .forEach(borrow -> log.warn("Salesforce error for Borrow [UUID: {}]: {}", borrow.getExternalBorrowUuid(), borrow.getErrors()));
        return borrows;
    }
}
