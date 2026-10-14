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
        assertEquals("ACTIVE", response.status());
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
        UserRequest request = new UserRequest("test@example.com", "Test User", null);

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
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        User user = User.builder()
                .uuid(uuid)
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .role(com.kovanlabs.librarymanagement.database.enums.RoleEnum.ADMIN)
                .phone("+15555555555")
                .status(com.kovanlabs.librarymanagement.database.enums.AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus.PENDING)
                .lastLoginAt(now)
                .inactivityWarningSentAt(now.minusDays(5))
                .build();

        var sObject = userMapper.toContactSObject(user);
        assertNotNull(sObject);
        assertEquals(uuid, sObject.getExternalUserUuid());
        assertEquals(1L, sObject.getLegacyUserId());
        assertEquals("Alice", sObject.getLastName());
        assertEquals("alice@example.com", sObject.getEmail());
        assertEquals("ADMIN", sObject.getRole());
        assertEquals("+15555555555", sObject.getPhone());
        assertEquals("DEACTIVATED", sObject.getStatus());
        assertEquals("PENDING", sObject.getReactivationRequestStatus());
        assertEquals(now.toString(), sObject.getLastLoginAt());
        assertEquals(now.minusDays(5).toString(), sObject.getInactivityWarningSentAt());
        assertEquals(0, sObject.getReward());

        User activeUser = User.builder()
                .uuid(uuid)
                .id(2L)
                .name("Active User")
                .status(com.kovanlabs.librarymanagement.database.enums.AccountStatus.ACTIVE)
                .build();
        var activeSObject = userMapper.toContactSObject(activeUser);
        assertNull(activeSObject.getReward());

        assertNull(userMapper.toContactSObject((User) null));
    }

    @Test
    void testToContactSObject_withUserResponse() {
        String uuid = UUID.randomUUID().toString();
        UserResponse dto = new UserResponse(uuid, 2L, "Bob", "bob@example.com", "USER", 10, "+15555555555", "DEACTIVATED", true, false, 0.0, "PENDING");

        var sObject = userMapper.toContactSObject(dto);
        assertNotNull(sObject);
        assertEquals(uuid, sObject.getExternalUserUuid());
        assertEquals(2L, sObject.getLegacyUserId());
        assertEquals("Bob", sObject.getLastName());
        assertEquals("bob@example.com", sObject.getEmail());
        assertEquals("USER", sObject.getRole());
        assertEquals("+15555555555", sObject.getPhone());
        assertEquals("DEACTIVATED", sObject.getStatus());
        assertEquals("PENDING", sObject.getReactivationRequestStatus());

        assertEquals(10, sObject.getReward());

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
                .phone("+15555555555")
                .status("DEACTIVATED")
                .reactivationRequestStatus("PENDING")
                .reward(75)
                .build();

        UserResponse res = userMapper.toUserResponse(contact);
        assertNotNull(res);
        assertEquals(uuid, res.uuid());
        assertEquals(42L, res.id());
        assertEquals("Charlie", res.name());
        assertEquals("charlie@example.com", res.email());
        assertEquals("ADMIN", res.role());
        assertEquals("+15555555555", res.phone());
        assertEquals("DEACTIVATED", res.status());
        assertEquals("PENDING", res.reactivationRequestStatus());
        assertEquals(75, res.rewardPoints());
        assertTrue(res.reactivationRequested());

        List<UserResponse> list = userMapper.toUserResponseList(List.of(contact));
        assertEquals(1, list.size());
        assertEquals("+15555555555", list.get(0).phone());
        assertEquals(75, list.get(0).rewardPoints());
        assertNull(userMapper.toUserResponseList(null));
    }

    @Test
    void testMapToResponse_WithRewardPoints() {
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .uuid(uuid)
                .id(99L)
                .name("Diana")
                .email("diana@example.com")
                .role(com.kovanlabs.librarymanagement.database.enums.RoleEnum.USER)
                .phone("+11234567890")
                .status(com.kovanlabs.librarymanagement.database.enums.AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus.APPROVED)
                .build();

        UserResponse res = userMapper.mapToResponse(user, 150);

        assertNotNull(res);
        assertEquals(uuid, res.uuid());
        assertEquals(99L, res.id());
        assertEquals("Diana", res.name());
        assertEquals("diana@example.com", res.email());
        assertEquals("USER", res.role());
        assertEquals("+11234567890", res.phone());
        assertEquals(150, res.rewardPoints());
        assertEquals("DEACTIVATED", res.status());
        assertEquals("APPROVED", res.reactivationRequestStatus());

        assertNull(userMapper.mapToResponse((User) null, 50));
    }

    @Test
    void testMapToResponse_UserListWithRewardMap() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();

        User user1 = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("User 1")
                .email("u1@example.com")
                .status(com.kovanlabs.librarymanagement.database.enums.AccountStatus.ACTIVE)
                .build();

        User user2 = User.builder()
                .uuid(uuid2)
                .id(2L)
                .name("User 2")
                .email("u2@example.com")
                .status(com.kovanlabs.librarymanagement.database.enums.AccountStatus.DEACTIVATED)
                .build();

        java.util.Map<String, Integer> rewardMap = java.util.Map.of(uuid1, 200, uuid2, 350);

        List<UserResponse> responses = userMapper.mapToResponse(List.of(user1, user2), rewardMap);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(200, responses.get(0).rewardPoints());
        assertEquals("ACTIVE", responses.get(0).status());
        assertEquals(350, responses.get(1).rewardPoints());
        assertEquals("DEACTIVATED", responses.get(1).status());

        assertTrue(userMapper.mapToResponse(List.of(), rewardMap).isEmpty());
        assertTrue(userMapper.mapToResponse(null, rewardMap).isEmpty());
    }
}
