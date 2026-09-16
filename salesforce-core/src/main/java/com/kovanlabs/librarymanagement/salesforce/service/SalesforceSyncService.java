package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.salesforce.builder.SOQLBuilder;
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

@Service
public class SalesforceSyncService implements SalesforceUserSyncDelegate {

    private static final Logger log = LoggerFactory.getLogger(SalesforceSyncService.class);

    private final SalesforceClientService clientService;
    private final SalesforceMapper salesforceMapper;

    public SalesforceSyncService(SalesforceClientService clientService, SalesforceMapper salesforceMapper) {
        this.clientService = clientService;
        this.salesforceMapper = salesforceMapper;
    }

    // --- WRITE OPERATIONS (SObject -> Map payload -> Salesforce) ---

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

    public void syncContact(ContactSObject contact) {
        if (contact == null || contact.getExternalUserUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(contact);
            clientService.upsertByExternalId(
                    ContactSObject.SOBJECT_NAME,
                    ContactSObject.EXTERNAL_ID_FIELD,
                    contact.getExternalUserUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Contact [UUID: {}]: {}", contact.getExternalUserUuid(), e.getMessage());
        }
    }

    public void syncBook(BookSObject book) {
        if (book == null || book.getExternalBookUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(book);
            clientService.upsertByExternalId(
                    BookSObject.SOBJECT_NAME,
                    BookSObject.EXTERNAL_ID_FIELD,
                    book.getExternalBookUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Book [UUID: {}]: {}", book.getExternalBookUuid(), e.getMessage());
        }
    }

    public void syncBorrow(BorrowSObject borrow) {
        if (borrow == null || borrow.getExternalBorrowUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toPayloadMap(borrow);
            clientService.upsertByExternalId(
                    BorrowSObject.SOBJECT_NAME,
                    BorrowSObject.EXTERNAL_ID_FIELD,
                    borrow.getExternalBorrowUuid(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Borrow [UUID: {}]: {}", borrow.getExternalBorrowUuid(), e.getMessage());
        }
    }

    // --- READ OPERATIONS (SOQL -> SObjects) ---

    @Override
    public List<UserResponse> fetchUsersFromSalesforce() {
        List<ContactSObject> contacts = fetchContactsFromSalesforce();
        return salesforceMapper.toUserResponseList(contacts);
    }

    public List<ContactSObject> fetchContactsFromSalesforce() {
        String soql = SOQLBuilder.fromSObjectClass(ContactSObject.class)
                .whereNotNull(ContactSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        return salesforceMapper.toSObjectList(json, ContactSObject.class);
    }

    public long getTotalBooksFromSalesforce() {
        String soql = SOQLBuilder.fromSObjectClass(BookSObject.class)
                .count()
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("totalSize")) {
            return 0;
        }
        return json.path("totalSize").asLong();
    }

    public List<BookSObject> fetchBooksFromSalesforce(int size, int offset) {
        String soql = SOQLBuilder.fromSObjectClass(BookSObject.class)
                .whereNotNull(BookSObject.EXTERNAL_ID_FIELD)
                .limit(size)
                .offset(offset)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        return salesforceMapper.toSObjectList(json, BookSObject.class);
    }

    public List<BorrowSObject> fetchBorrowsFromSalesforce() {
        String soql = SOQLBuilder.fromSObjectClass(BorrowSObject.class)
                .whereNotNull(BorrowSObject.EXTERNAL_ID_FIELD)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        return salesforceMapper.toSObjectList(json, BorrowSObject.class);
    }
}
