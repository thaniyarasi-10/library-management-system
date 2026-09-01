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
        userMapper = new UserMapperImpl();
    }

    @Test
    void testMapToResponse_SingleUser() {
        UUID uuid = UUID.randomUUID();
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
        UserRequest request = new UserRequest("test@example.com", "password123", "Test User");

        User entity = userMapper.mapToEntity(request);

        assertNotNull(entity);
        assertEquals("test@example.com", entity.getEmail());
        assertEquals("Test User", entity.getName());
    }

    @Test
    void testMapToEntity_NullRequest() {
        assertNull(userMapper.mapToEntity(null));
    }
}
