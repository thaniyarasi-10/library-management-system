package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.salesforce.builder.SOQLBuilder;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BookFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.BorrowFields;
import com.kovanlabs.librarymanagement.salesforce.constant.fields.ContactFields;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.mapping.SalesforceMapper;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.SalesforceUserSyncDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SalesforceSyncService implements SalesforceUserSyncDelegate {

    private static final Logger log = LoggerFactory.getLogger(SalesforceSyncService.class);

    private final SalesforceClientService clientService;
    private final SalesforceMapper salesforceMapper;

    public SalesforceSyncService(SalesforceClientService clientService, SalesforceMapper salesforceMapper) {
        this.clientService = clientService;
        this.salesforceMapper = salesforceMapper;
    }

    @Override
    public void syncUser(Object userObj) {
        if (userObj instanceof User u) {
            syncUser(u);
        }
    }

    // --- WRITE OPERATIONS ---

    public void syncUser(User user) {
        if (user == null || user.getUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toContactFields(user);
            clientService.upsertByExternalId(
                    SObject.CONTACT.getObjectName(),
                    ContactFields.EXTERNAL_USER_UUID,
                    user.getUuid().toString(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for User [UUID: {}]: {}", user.getUuid(), e.getMessage());
        }
    }

    public void syncBook(Book book) {
        if (book == null || book.getUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toBookFields(book);
            clientService.upsertByExternalId(
                    SObject.BOOK.getObjectName(),
                    BookFields.EXTERNAL_BOOK_UUID,
                    book.getUuid().toString(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Book [UUID: {}]: {}", book.getUuid(), e.getMessage());
        }
    }

    public void syncBorrow(Borrow borrow) {
        if (borrow == null || borrow.getUuid() == null) {
            return;
        }
        try {
            Map<String, Object> fields = salesforceMapper.toBorrowFields(borrow);
            clientService.upsertByExternalId(
                    SObject.BORROW.getObjectName(),
                    BorrowFields.EXTERNAL_BORROW_UUID,
                    borrow.getUuid().toString(),
                    fields
            );
        } catch (Exception e) {
            log.error("Salesforce sync error for Borrow [UUID: {}]: {}", borrow.getUuid(), e.getMessage());
        }
    }

    // --- READ OPERATIONS (SOQL QUERIES) ---

    public List<UserResponse> fetchUsersFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(ContactFields.LAST_NAME,
                        ContactFields.EMAIL,
                        ContactFields.EXTERNAL_USER_UUID)
                .from(SObject.CONTACT)
                .whereNotNull(ContactFields.EXTERNAL_USER_UUID)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("records")) {
            return Collections.emptyList();
        }

        List<UserResponse> list = new ArrayList<>();
        for (JsonNode node : json.path("records")) {
            UserResponse userResponse = salesforceMapper.toUserResponse(node);
            if (userResponse != null) {
                list.add(userResponse);
            }
        }
        return list;
    }

    public long getTotalBooksFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .count()
                .from(SObject.BOOK)
                .whereNotNull(BookFields.EXTERNAL_BOOK_UUID)
                .build();

        JsonNode json = clientService.query(soql);
        if (json == null || !json.has("totalSize")) {
            return 0;
        }
        return json.path("totalSize").asLong();
    }

    public JsonNode fetchBooksJsonFromSalesforce(int size, int offset) {
        String soql = new SOQLBuilder<>()
                .select(BookFields.NAME,
                        BookFields.TITLE,
                        BookFields.AUTHOR,
                        BookFields.ISBN,
                        BookFields.COVER_IMAGE_URL,
                        BookFields.EXTERNAL_BOOK_UUID)
                .from(SObject.BOOK)
                .whereNotNull(BookFields.EXTERNAL_BOOK_UUID)
                .limit(size)
                .offset(offset)
                .build();

        return clientService.query(soql);
    }

    public JsonNode fetchBorrowsJsonFromSalesforce() {
        String soql = new SOQLBuilder<>()
                .select(
                        BorrowFields.EXTERNAL_BORROW_UUID,
                        BorrowFields.BORROW_DATE,
                        BorrowFields.DUE_DATE,
                        BorrowFields.RETURN_DATE,
                        BorrowFields.BORROW_STATUS,
                        BorrowFields.CONTACT_LAST_NAME,
                        BorrowFields.CONTACT_EMAIL,
                        BorrowFields.CONTACT_EXTERNAL_USER_UUID,
                        BorrowFields.BOOK_NAME,
                        BorrowFields.BOOK_TITLE,
                        BorrowFields.BOOK_AUTHOR,
                        BorrowFields.BOOK_COVER_IMAGE_URL,
                        BorrowFields.BOOK_EXTERNAL_BOOK_UUID
                )
                .from(SObject.BORROW)
                .whereNotNull(BorrowFields.EXTERNAL_BORROW_UUID)
                .build();

        return clientService.query(soql);
    }
}
