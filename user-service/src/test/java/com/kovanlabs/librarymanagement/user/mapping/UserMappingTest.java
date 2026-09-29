package com.kovanlabs.librarymanagement.user.mapping;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = Mappers.getMapper(UserMapper.class);
    }


    @Test
    void testMapToResponse_SingleUser() {
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .uuid(uuid)
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .build();

        UserResponse response = userMapper.mapToResponse(user);

        assertNotNull(response);
        assertEquals(uuid, response.uuid());
        assertEquals(1L, response.id());
        assertEquals("John Doe", response.name());
        assertEquals("john@example.com", response.email());
    }

    @Test
    void testMapToResponse_NullUser() {
        assertNull(userMapper.mapToResponse((User) null));
    }

    @Test
    void testMapToResponse_UserList() {
        User user1 = User.builder().id(1L).name("User 1").email("user1@example.com").build();
        User user2 = User.builder().id(2L).name("User 2").email("user2@example.com").build();

        List<UserResponse> responses = userMapper.mapToResponse(List.of(user1, user2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("User 1", responses.get(0).name());
        assertEquals("User 2", responses.get(1).name());
    }

    @Test
    void testMapToResponse_NullList() {
        List<UserResponse> responses = userMapper.mapToResponse((List<User>) null);
        assertNull(responses);
    }

    @Test
    void testMapToEntity() {
        UserRequest request = new UserRequest("test@example.com", "Test User");

        User entity = userMapper.mapToEntity(request);

        assertNotNull(entity);
        assertEquals("test@example.com", entity.getEmail());
        assertEquals("Test User", entity.getName());
    }

    @Test
    void testMapToEntity_NullRequest() {
        assertNull(userMapper.mapToEntity(null));
    }

    @Test
    void testToContactSObject_withUserEntity() {
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .uuid(uuid)
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .role(com.kovanlabs.librarymanagement.database.enums.RoleEnum.ADMIN)
                .build();

        var sObject = userMapper.toContactSObject(user);
        assertNotNull(sObject);
        assertEquals(uuid, sObject.getExternalUserUuid());
        assertEquals(1L, sObject.getLegacyUserId());
        assertEquals("Alice", sObject.getLastName());
        assertEquals("alice@example.com", sObject.getEmail());
        assertEquals("ADMIN", sObject.getRole());

        assertNull(userMapper.toContactSObject((User) null));
    }

    @Test
    void testToContactSObject_withUserResponse() {
        String uuid = UUID.randomUUID().toString();
        UserResponse dto = new UserResponse(uuid, 2L, "Bob", "bob@example.com", "USER", 10);

        var sObject = userMapper.toContactSObject(dto);
        assertNotNull(sObject);
        assertEquals(uuid, sObject.getExternalUserUuid());
        assertEquals(2L, sObject.getLegacyUserId());
        assertEquals("Bob", sObject.getLastName());
        assertEquals("bob@example.com", sObject.getEmail());
        assertEquals("USER", sObject.getRole());

        assertNull(userMapper.toContactSObject((UserResponse) null));
    }

    @Test
    void testToUserResponse_and_toUserResponseList() {
        String uuid = UUID.randomUUID().toString();
        var contact = com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject.builder()
                .externalUserUuid(uuid)
                .legacyUserId(42L)
                .lastName("Charlie")
                .email("charlie@example.com")
                .role("ADMIN")
                .build();

        UserResponse res = userMapper.toUserResponse(contact);
        assertNotNull(res);
        assertEquals(uuid, res.uuid());
        assertEquals(42L, res.id());
        assertEquals("Charlie", res.name());
        assertEquals("charlie@example.com", res.email());
        assertEquals("ADMIN", res.role());

        List<UserResponse> list = userMapper.toUserResponseList(List.of(contact));
        assertEquals(1, list.size());
        assertNull(userMapper.toUserResponseList(null));
    }
}
