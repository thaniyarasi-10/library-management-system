package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @InjectMocks
    private UserServiceImpl userService;

    private User user1;
    private User user2;
    private UUID uuid1;
    private UUID uuid2;

    @BeforeEach
    void setUp() {
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();

        user1 = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .password("encodedPassword")
                .build();

        user2 = User.builder()
                .uuid(uuid2)
                .id(2L)
                .name("Bob Jones")
                .email("bob@example.com")
                .build();
    }

    @Test
    void createUser_shouldEncodePasswordAndSave() {
        UserRequest request = new UserRequest("alice@example.com", "Password123!", "Alice Smith");
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user1);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("Alice Smith", response.name());
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getAllUsers_shouldReturnList() {
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<UserResponse> users = userService.getAllUsers();

        assertEquals(2, users.size());
    }

    @Test
    void getAllUsers_paginated_shouldReturnPagedResponse() {
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    @DisplayName("searchUsers should return PagedResponse with matching users")
    void searchUsers_ShouldReturnPagedResponse() {
        List<User> users = List.of(user1);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<User> usersPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.searchUsers(eq("Alice"), any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.searchUsers("Alice", 0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Alice Smith", response.content().get(0).name());
    }

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        UserResponse response = userService.getUserById(1L);

        assertEquals("Alice Smith", response.name());
    }

    @Test
    void getUserById_whenUserNotFound_shouldThrowResponseStatusException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.getUserById(99L));
    }

    @Test
    void updateUser_whenUserExists_shouldUpdateEmailAndSave() {
        UserRequest request = new UserRequest("updated@example.com", null, "Alice Smith");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
    }

    @Test
    void deleteUser_whenUserExists_shouldDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
    }

    @Test
    void findOrCreateGoogleUser_whenExistingUserWithoutProvider_shouldUpdateProvider() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.findOrCreateGoogleUser("google-123", "alice@example.com", "Alice Smith");

        assertEquals("google-123", result.getProviderId());
        assertEquals(AuthProvider.GOOGLE_OAUTH, result.getProvider());
    }

    @Test
    void findOrCreateGoogleUser_whenNewUser_shouldCreateUser() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.findOrCreateGoogleUser("google-456", "new@example.com", "New User");

        assertEquals("new@example.com", result.getEmail());
        assertEquals("google-456", result.getProviderId());
        assertEquals(RoleEnum.USER, result.getRole());
    }
}
