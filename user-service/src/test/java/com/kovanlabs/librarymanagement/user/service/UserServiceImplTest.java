package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
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
    private UserProviderRepository userProviderRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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

    @Mock
    private SalesforceUserSyncDelegate salesforceSyncDelegate;

    @Mock
    private Auth0RoleSyncDelegate auth0RoleSyncDelegate;

    @Test
    void createUser_shouldEncodePasswordAndSave() {
        UserRequest request = new UserRequest("alice@example.com", "Password123!", "Alice Smith");
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("Alice Smith", response.name());
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void createUser_whenSalesforceSyncSucceeds_shouldMarkStatusSuccess() {
        UserRequest request = new UserRequest("alice@example.com", "Password123!", "Alice Smith");
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        verify(salesforceSyncDelegate).syncUser(any(UserResponse.class));
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void createUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        UserRequest request = new UserRequest("alice@example.com", "Password123!", "Alice Smith");
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncDelegate).syncUser(any(UserResponse.class));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        verify(userRepository, times(2)).save(any(User.class));
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
        verify(salesforceSyncDelegate).syncUser(any(UserResponse.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
    }

    @Test
    void updateUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        UserRequest request = new UserRequest("updated@example.com", null, "Alice Smith");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce update error")).when(salesforceSyncDelegate).syncUser(any(UserResponse.class));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        assertEquals(SalesforceSyncStatus.PENDING, user1.getSalesforceSyncStatus());
        assertEquals(1, user1.getSalesforceRetryCount());
    }

    @Test
    void deleteUser_whenUserExists_shouldDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
    }

    @Test
    void syncAuth0User_whenExistingProviderId_shouldReturnExistingUserWithoutCallingAuth0Sync() {
        user1.setRole(RoleEnum.USER);
        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|12345")
                .build();
        when(userProviderRepository.findByProviderId("auth0|12345")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User("auth0|12345", "alice@example.com", "Alice Smith");

        assertNotNull(result);
        assertEquals(user1.getUuid(), result.getUuid());
        assertEquals(RoleEnum.USER, result.getRole());
        verify(userRepository, never()).findByEmail(anyString());
        verify(auth0RoleSyncDelegate, never()).syncUserRole(anyString(), any());
    }

    @Test
    void syncAuth0User_whenExistingEmailWithoutProviderId_shouldLinkProviderIdAndSyncExistingRole() {
        user1.setRole(RoleEnum.ADMIN);
        when(userProviderRepository.findByProviderId("auth0|new-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.findByUserUuidAndProvider(user1.getUuid(), AuthProvider.AUTH0)).thenReturn(Optional.empty());

        User result = userService.syncAuth0User("auth0|new-sub", "alice@example.com", "Alice Smith");

        assertNotNull(result);
        assertEquals(RoleEnum.ADMIN, result.getRole());
        verify(userRepository, times(1)).save(user1);
        verify(userProviderRepository, times(1)).save(any(com.kovanlabs.librarymanagement.database.entity.UserProvider.class));
        verify(auth0RoleSyncDelegate, times(1)).syncUserRole("auth0|new-sub", RoleEnum.ADMIN);
    }

    @Test
    void syncAuth0User_whenNoExistingUser_shouldCreateNewUserWithRoleUserAndSyncAuth0() {
        when(userProviderRepository.findByProviderId("auth0|brand-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("brandnew@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUuid(UUID.randomUUID());
            return u;
        });

        User result = userService.syncAuth0User("auth0|brand-new", "brandnew@example.com", "Brand New");

        assertEquals("brandnew@example.com", result.getEmail());
        assertEquals(RoleEnum.USER, result.getRole());
        verify(userRepository, times(1)).save(any(User.class));
        verify(userProviderRepository, times(1)).save(any(com.kovanlabs.librarymanagement.database.entity.UserProvider.class));
        verify(auth0RoleSyncDelegate, times(1)).syncUserRole("auth0|brand-new", RoleEnum.USER);
    }

    @Test
    void syncAuth0User_whenSameSubUsedAgain_shouldNotCreateDuplicateUser() {
        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|existing")
                .build();
        when(userProviderRepository.findByProviderId("auth0|existing")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result1 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith");
        User result2 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith");

        assertEquals(result1, result2);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRole_whenUserExists_shouldUpdateRoleAndSyncToAuth0() {
        user1.setRole(RoleEnum.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|user1-sub")
                .build();
        when(userProviderRepository.findByUserUuid(user1.getUuid()))
                .thenReturn(List.of(up));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(RoleEnum.ADMIN, user1.getRole());
        verify(userRepository, times(1)).save(user1);
        verify(auth0RoleSyncDelegate, times(1)).syncUserRole("auth0|user1-sub", RoleEnum.ADMIN);
    }

    @Test
    void updateUserRole_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.updateUserRole(99L, RoleEnum.ADMIN));
    }

    @Test
    void getUserByIdentifier_whenFoundByProviderId_shouldReturnUser() {
        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|alice")
                .build();
        when(userProviderRepository.findByProviderId("auth0|alice")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier("auth0|alice");

        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenNotFound_shouldThrowNotFound() {
        when(userProviderRepository.findByProviderId("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier("unknown"));
    }
}
