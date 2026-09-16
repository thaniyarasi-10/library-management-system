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

@Component
@RequiredArgsConstructor
public class SalesforceMapper {

    private final ObjectMapper objectMapper;

    // --- User DTO <-> ContactSObject Mappings ---

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

    public UserResponse toUserResponse(ContactSObject contact) {
        if (contact == null) {
            return null;
        }
        UUID uuid = parseUUID(contact.getExternalUserUuid());
        return new UserResponse(uuid, null, contact.getLastName(), contact.getEmail(), 0);
    }

    public List<UserResponse> toUserResponseList(List<ContactSObject> contacts) {
        if (contacts == null) {
            return Collections.emptyList();
        }
        return contacts.stream()
                .map(this::toUserResponse)
                .toList();
    }

    // --- SObject -> Map Payload (Writes) ---

    public Map<String, Object> toPayloadMap(Object sObject) {
        if (sObject == null) {
            return Collections.emptyMap();
        }
        return objectMapper.convertValue(sObject, new TypeReference<Map<String, Object>>() {});
    }

    // --- JSON / JsonNode to SObject Deserialization ---

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
