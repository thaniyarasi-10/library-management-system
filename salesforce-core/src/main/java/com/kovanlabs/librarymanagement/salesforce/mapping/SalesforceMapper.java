package com.kovanlabs.librarymanagement.salesforce.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mapper component for converting between User DTOs, JSON payloads, and Salesforce SObjects.
 */
@Component
@RequiredArgsConstructor
public class SalesforceMapper {

    private final ObjectMapper objectMapper;

    // --- User DTO <-> ContactSObject Mappings ---

    /**
     * Converts a {@link UserResponse} DTO to a {@link ContactSObject} model.
     *
     * @param user The user response DTO
     * @return The populated {@link ContactSObject}, or {@code null} if input is null
     */
    public ContactSObject toContactSObject(UserResponse user) {
        if (user == null) {
            return null;
        }
        String lastName = (user.name() != null && !user.name().isBlank())
                ? user.name()
                : user.email();

        return ContactSObject.builder()
                .externalUserUuid(user.uuid() != null ? user.uuid().toString() : null)
                .lastName(lastName)
                .email(user.email())
                .build();
    }

    /**
     * Converts a {@link ContactSObject} model from Salesforce to a {@link UserResponse} DTO.
     *
     * @param contact The Contact SObject
     * @return The converted {@link UserResponse} DTO, or {@code null} if input is null
     */
    public UserResponse toUserResponse(ContactSObject contact) {
        if (contact == null) {
            return null;
        }
        UUID uuid = parseUUID(contact.getExternalUserUuid());
        return new UserResponse(uuid, null, contact.getLastName(), contact.getEmail(), 0);
    }

    /**
     * Converts a list of {@link ContactSObject} models to a list of {@link UserResponse} DTOs.
     *
     * @param contacts The list of Contact SObjects
     * @return List of converted {@link UserResponse} DTOs
     */
    public List<UserResponse> toUserResponseList(List<ContactSObject> contacts) {
        if (contacts == null) {
            return Collections.emptyList();
        }
        return contacts.stream()
                .map(this::toUserResponse)
                .toList();
    }

    // --- SObject -> Map Payload (Writes) ---

    /**
     * Converts an SObject model into a key-value Map payload suitable for Salesforce REST API requests.
     *
     * @param sObject The SObject model instance
     * @return Map containing Salesforce field names and values
     */
    public Map<String, Object> toPayloadMap(Object sObject) {
        if (sObject == null) {
            return Collections.emptyMap();
        }
        return objectMapper.convertValue(sObject, new TypeReference<Map<String, Object>>() {});
    }

    // --- JSON / JsonNode to SObject Deserialization ---

    /**
     * Deserializes a single Jackson {@link JsonNode} record into an SObject instance of the specified class.
     *
     * @param <T> The target SObject type
     * @param node The JSON node to deserialize
     * @param clazz The class of the target SObject
     * @return The deserialized SObject instance, or {@code null} on failure
     */
    public <T> T toSObject(JsonNode node, Class<T> clazz) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, clazz);
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
    public <T> List<T> toSObjectList(JsonNode root, Class<T> clazz) {
        if (root == null || !root.has("records")) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>();
        for (JsonNode record : root.path("records")) {
            T obj = toSObject(record, clazz);
            if (obj != null) {
                list.add(obj);
            }
        }
        return list;
    }

    /**
     * Safely parses a UUID string without throwing exceptions.
     *
     * @param str The UUID string to parse
     * @return The parsed {@link UUID}, or {@code null} if parsing fails or input is blank
     */
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
