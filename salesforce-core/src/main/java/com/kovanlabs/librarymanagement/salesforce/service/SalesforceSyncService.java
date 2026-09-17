package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.salesforce.builder.SOQLBuilder;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.mapping.SalesforceMapper;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.SalesforceUserSyncDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for synchronizing User, Book, and Borrow entities with Salesforce SObjects.
 * <p>
 * Implements {@link SalesforceUserSyncDelegate} to integrate with user module workflows,
 * and provides methods to push and pull SObjects to/from Salesforce via SOQL queries and REST APIs.
 */
@Service
public class SalesforceSyncService implements SalesforceUserSyncDelegate {

    private static final Logger log = LoggerFactory.getLogger(SalesforceSyncService.class);

    private final SalesforceClientService clientService;
    private final SalesforceMapper salesforceMapper;

    /**
     * Constructs a new {@link SalesforceSyncService} with required dependencies.
     *
     * @param clientService The Salesforce REST client service
     * @param salesforceMapper The mapper for converting entities/DTOs to/from SObjects
     */
    public SalesforceSyncService(SalesforceClientService clientService, SalesforceMapper salesforceMapper) {
        this.clientService = clientService;
        this.salesforceMapper = salesforceMapper;
    }

    // --- WRITE OPERATIONS (SObject -> Map payload -> Salesforce) ---

    /**
     * Synchronizes a User record to Salesforce Contact asynchronously/safely.
     *
     * @param user The user response DTO to sync
     */
    @Override
    public void syncUser(UserResponse user) {
        if (user == null || user.uuid() == null) {
            return;
        }
        try {
            ContactSObject contact = salesforceMapper.toContactSObject(user);
            syncContact(contact);
        } catch (Exception e) {
            log.error("Salesforce sync error for User [UUID: {}]: {}", user.uuid(), e.getMessage());
        }
    }

    /**
     * Synchronizes a {@link ContactSObject} to Salesforce via upsert on External ID.
     *
     * @param contact The Contact SObject to upsert
     */
    public void syncContact(ContactSObject contact) {
        if (contact == null || contact.getExternalUserUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(contact);
            clientService.upsertByExternalId(
                    SObject.CONTACT.getObjectName(),
                    ContactSObject.EXTERNAL_ID_FIELD,
                    contact.getExternalUserUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Contact [UUID: {}]: {}", contact.getExternalUserUuid(), e.getMessage());
        }
    }

    /**
     * Synchronizes a {@link BookSObject} to Salesforce via upsert on External ID.
     *
     * @param book The Book SObject to upsert
     */
    public void syncBook(BookSObject book) {
        if (book == null || book.getExternalBookUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(book);
            clientService.upsertByExternalId(
                    SObject.BOOK.getObjectName(),
                    BookSObject.EXTERNAL_ID_FIELD,
                    book.getExternalBookUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Book [UUID: {}]: {}", book.getExternalBookUuid(), e.getMessage());
        }
    }

    /**
     * Synchronizes a {@link BorrowSObject} to Salesforce via upsert on External ID.
     *
     * @param borrow The Borrow SObject to upsert
     */
    public void syncBorrow(BorrowSObject borrow) {
        if (borrow == null || borrow.getExternalBorrowUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(borrow);
            clientService.upsertByExternalId(
                    SObject.BORROW.getObjectName(),
                    BorrowSObject.EXTERNAL_ID_FIELD,
                    borrow.getExternalBorrowUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Borrow [UUID: {}]: {}", borrow.getExternalBorrowUuid(), e.getMessage());
        }
    }

    // --- READ OPERATIONS (SOQL -> SObjects) ---

    /**
     * Fetches all synced users from Salesforce Contact records and converts them to {@link UserResponse} DTOs.
     *
     * @return List of {@link UserResponse} records from Salesforce
     */
    @Override
    public List<UserResponse> fetchUsersFromSalesforce() {
        List<ContactSObject> contacts = fetchContactsFromSalesforce();
        return salesforceMapper.toUserResponseList(contacts);
    }

    /**
     * Queries and fetches all {@link ContactSObject} records from Salesforce that have an external UUID.
     *
     * @return List of {@link ContactSObject} instances
     */
    public List<ContactSObject> fetchContactsFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(ContactFields.EXTERNAL_USER_UUID, ContactFields.LAST_NAME, ContactFields.EMAIL)
                .from(SObject.CONTACT)
                .whereNotNull(ContactSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        List<ContactSObject> contacts = salesforceMapper.toSObjectList(json, ContactSObject.class);
        for (ContactSObject contact : contacts) {
            if (contact != null) {
                log.warn("Salesforce error for Contact [UUID: {}]: {}", contact.getExternalUserUuid(), contact.getErrors());
            }
        }
        return contacts;
    }

    /**
     * Retrieves the total count of {@link BookSObject} records currently present in Salesforce.
     *
     * @return The total count of book records in Salesforce
     */
    public long getTotalBooksFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .count()
                .from(SObject.BOOK)
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("totalSize")) {
            return 0;
        }
        return json.path("totalSize").asLong();
    }

    /**
     * Fetches a paginated list of {@link BookSObject} records from Salesforce.
     *
     * @param size Number of records to return (LIMIT)
     * @param offset Number of records to skip (OFFSET)
     * @return List of {@link BookSObject} instances
     */
    public List<BookSObject> fetchBooksFromSalesforce(int size, int offset) {
        String soql = new SOQLBuilder<>()
                .select(BookFields.EXTERNAL_BOOK_UUID, BookFields.NAME, BookFields.TITLE, BookFields.AUTHOR, BookFields.ISBN, BookFields.COVER_IMAGE_URL)
                .from(SObject.BOOK)
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .limit(size)
                .offset(offset)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        List<BookSObject> books = salesforceMapper.toSObjectList(json, BookSObject.class);
        for (BookSObject book : books) {
            if (book != null) {
                log.warn("Salesforce error for Book [UUID: {}]: {}", book.getExternalBookUuid(), book.getErrors());
            }
        }
        return books;
    }

    /**
     * Queries and fetches all {@link BorrowSObject} records from Salesforce that have an external UUID.
     *
     * @return List of {@link BorrowSObject} instances
     */
    public List<BorrowSObject> fetchBorrowsFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(BorrowFields.EXTERNAL_BORROW_UUID, BorrowFields.BORROW_DATE, BorrowFields.DUE_DATE, BorrowFields.RETURN_DATE, BorrowFields.BORROW_STATUS)
                .from(SObject.BORROW)
                .whereNotNull(BorrowSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        List<BorrowSObject> borrows = salesforceMapper.toSObjectList(json, BorrowSObject.class);
        for (BorrowSObject borrow : borrows) {
            if (borrow != null ) {
                log.warn("Salesforce error for Borrow [UUID: {}]: {}", borrow.getExternalBorrowUuid(), borrow.getErrors());
            }
        }
        return borrows;
    }
}
