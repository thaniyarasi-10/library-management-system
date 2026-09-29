package com.kovanlabs.librarymanagement.salesforce.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Service interface for synchronizing Contacts, Books, and Borrows with Salesforce SObjects.
 */
public interface SalesforceSync {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Synchronizes a contact SObject model with Salesforce Contact records.
     *
     * @param contact The contact SObject model to sync
     */
    void syncContact(ContactSObject contact);

    /**
     * Deletes a synced user record from Salesforce Contact by user UUID.
     *
     * @param userUuid The unique identifier of the user to delete
     */
    void deleteUser(UUID userUuid);

    /**
     * Fetches all synced contact records from Salesforce.
     *
     * @return List of ContactSObject models obtained from Salesforce Contact records
     */
    List<ContactSObject> fetchContactsFromSalesforce();

    /**
     * Synchronizes a Book SObject model with Salesforce Book records.
     *
     * @param book The book SObject model to sync
     */
    void syncBook(BookSObject book);

    /**
     * Synchronizes a Borrow SObject model with Salesforce Borrow records.
     *
     * @param borrow The borrow SObject model to sync
     */
    void syncBorrow(BorrowSObject borrow);

    /**
     * Retrieves the total count of Book records in Salesforce.
     *
     * @return The total count of book records
     */
    long getTotalBooksFromSalesforce();

    /**
     * Fetches a paginated list of Book SObjects from Salesforce.
     *
     * @param size Number of records to return
     * @param offset Number of records to skip
     * @return List of Book SObjects
     */
    List<BookSObject> fetchBooksFromSalesforce(int size, int offset);

    /**
     * Queries and fetches all Borrow SObjects from Salesforce.
     *
     * @return List of Borrow SObjects
     */
    List<BorrowSObject> fetchBorrowsFromSalesforce();

    // --- Helper Methods ---

    /**
     * Converts an SObject model into a key-value Map payload suitable for Salesforce REST API requests.
     *
     * @param sObject The SObject model instance
     * @return Map containing Salesforce field names and values
     */
    default Map<String, Object> toPayloadMap(Object sObject) {
        if (Objects.isNull(sObject)) {
            return Collections.emptyMap();
        }
        return OBJECT_MAPPER.convertValue(sObject, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Deserializes a single Jackson {@link JsonNode} record into an SObject instance of the specified class.
     *
     * @param <T> The target SObject type
     * @param node The JSON node to deserialize
     * @param clazz The class of the target SObject
     * @return The deserialized SObject instance, or {@code null} on failure
     */
    default <T> T toSObject(JsonNode node, Class<T> clazz) {
        if (Objects.isNull(node) || node.isNull()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(node, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deserializes a root Salesforce query response containing a "records" array into a list of SObjects.
     *
     * @param <T> The target SObject type
     * @param root The root JSON response node
     * @param clazz The class of the target SObject
     * @return List of deserialized SObjects
     */
    default <T> List<T> toSObjectList(JsonNode root, Class<T> clazz) {
        if (Objects.isNull(root) || !root.has("records")) {
            return Collections.emptyList();
        }
        return StreamSupport.stream(root.path("records").spliterator(), false)
                .map(record -> toSObject(record, clazz))
                .filter(Objects::nonNull)
                .toList();
    }

}
