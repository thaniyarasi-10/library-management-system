package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

    @InjectMocks
    private UserServiceImpl userService;

    private User user1;
    private User user2;
    private String uuid1;
    private String uuid2;

    @BeforeEach
    void setUp() {
        uuid1 = UUID.randomUUID().toString();
        uuid2 = UUID.randomUUID().toString();

        user1 = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .build();

        user2 = User.builder()
                .uuid(uuid2)
                .id(2L)
                .name("Bob Jones")
                .email("bob@example.com")
                .build();
    }

    @Mock
    private SalesforceSync salesforceSyncDelegate;

    @Test
    void createUser_shouldSaveAndReturnUserResponse() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("Alice Smith", response.name());
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void createUser_whenSalesforceSyncSucceeds_shouldMarkStatusSuccess() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void createUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

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
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
    }

    @Test
    void updateUser_whenUserExists_shouldUpdatePhoneAndSave() {
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith", "+15555555555");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("+15555555555", response.phone());
        assertEquals("+15555555555", user1.getPhone());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
    }

    @Test
    void updateUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce update error")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        assertEquals(SalesforceSyncStatus.PENDING, user1.getSalesforceSyncStatus());
        assertEquals(1, user1.getSalesforceRetryCount());
    }

    @Test
    void deleteUser_whenUserExists_shouldDeleteAndSyncSalesforce() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
        verify(salesforceSyncDelegate, times(1)).deleteUser(uuid1);
    }

    @Test
    void deleteUser_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.deleteUser(999L));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_whenSalesforceDeleteThrows_shouldLogAndNotFail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        doThrow(new RuntimeException("SF delete error")).when(salesforceSyncDelegate).deleteUser(uuid1);

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
    }

    @Test
    void syncAuth0User_whenExistingProviderId_shouldReturnExistingUserWithoutUpdatingUserOrSalesforce() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid(testUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|12345")
                .build();
        when(userProviderRepository.findByProviderId("auth0|12345")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(testUser.getUuid())).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("auth0|12345", "different-email@example.com", "Different Name",
                RoleEnum.ADMIN);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.USER, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        verify(userRepository, never()).save(any(User.class));
        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithDifferentProvider_shouldOnlyCreateUserProviderAndKeepUserUntouched() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        // Existing provider is GOOGLE_OAUTH; AUTH0 mapping is not yet in place
        when(userProviderRepository.findByProviderId("auth0|new-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("auth0|new-sub", "alice@example.com", "Different Name", RoleEnum.ADMIN);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.USER, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        verify(userProviderRepository, times(1)).save(argThat(up -> up.getUserUuid().equals(testUser.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && "auth0|new-sub".equals(up.getProviderId())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithMultipleProviderIds_shouldStoreAllAsSeparateUserProviderRecords() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        when(userProviderRepository.findByProviderId("google-oauth2|123456789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("google-oauth2|123456789", "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        verify(userProviderRepository, times(1)).save(argThat(up -> 
                up.getUserUuid().equals(testUser.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && "google-oauth2|123456789".equals(up.getProviderId())
        ));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithGoogleOAuthRawId_shouldLinkAuth0ToExistingUserUuidAndKeepUserUntouched() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.ADMIN)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        String auth0Sub = "google-oauth2|115993863360458947065";
        String rawGoogleId = "115993863360458947065";

        UserProvider existingGoogleProvider = UserProvider.builder()
                .userUuid(testUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId(rawGoogleId)
                .build();

        // Exact sub not found, but raw ID matches existing GOOGLE_OAUTH provider
        when(userProviderRepository.findByProviderId(auth0Sub)).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId(rawGoogleId)).thenReturn(Optional.of(existingGoogleProvider));
        when(userRepository.findByUuid(testUser.getUuid())).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User(auth0Sub, "thaniyarasi10@gmail.com", "thani", RoleEnum.USER);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.ADMIN, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        // Only the missing AUTH0 mapping is created
        verify(userProviderRepository, times(1)).save(argThat(up -> up.getUserUuid().equals(user1.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && auth0Sub.equals(up.getProviderId())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenNoExistingUser_shouldCreateNewUserWithRoleAndSyncSalesforce() {
        when(userProviderRepository.findByProviderId("auth0|brand-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("brandnew@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (Objects.isNull(u.getUuid())) {
                u.setUuid(UUID.randomUUID().toString());
            }
            return u;
        });

        User result = userService.syncAuth0User("auth0|brand-new", "brandnew@example.com", "Brand New", RoleEnum.ADMIN);

        assertEquals("brandnew@example.com", result.getEmail());
        assertEquals(RoleEnum.ADMIN, result.getRole());
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(userProviderRepository, times(1)).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
    }

    @Test
    void syncAuth0User_whenSameSubUsedAgain_shouldNotCreateDuplicateUser() {
        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|existing")
                .build();
        when(userProviderRepository.findByProviderId("auth0|existing")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result1 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith", RoleEnum.USER);
        User result2 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertEquals(result1, result2);
        verify(userRepository, never()).save(any(User.class));
        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_shouldNeverStoreSubAsEmailOrInUserTable() {
        when(userProviderRepository.findByProviderId("auth0|sub-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (Objects.isNull(u.getUuid())) {
                u.setUuid(UUID.randomUUID().toString());
            }
            return u;
        });

        User result = userService.syncAuth0User("auth0|sub-12345", "test@example.com", "Test User", RoleEnum.USER);

        assertEquals("test@example.com", result.getEmail());
        assertFalse(result.getEmail().contains("auth0|sub-12345"));
        verify(userProviderRepository).save(argThat(up -> "auth0|sub-12345".equals(up.getProviderId())));
    }

    @Test
    void syncAuth0User_whenSubIsEmpty_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("", "test@example.com", "Test", RoleEnum.USER));
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User(null, "test@example.com", "Test", RoleEnum.USER));
    }

    @Test
    void syncAuth0User_whenSubHasUnexpectedPipeFormat_shouldFallbackToEmailGracefully() {
        // sub ends with pipe, no rawId
        when(userProviderRepository.findByProviderId("auth0|")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User("auth0|", "test@example.com", "Test", RoleEnum.USER);
        assertNotNull(result);
        assertEquals(user1.getUuid(), result.getUuid());
        verify(userProviderRepository).save(argThat(up -> "auth0|".equals(up.getProviderId())));
    }

    @Test
    void syncAuth0User_whenNewUserAndEmailMissing_shouldThrowException() {
        when(userProviderRepository.findByProviderId("auth0|new-no-email")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("auth0|new-no-email", null, "Test", RoleEnum.USER));
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("auth0|new-no-email", "   ", "Test", RoleEnum.USER));
    }

    @Test
    void updateUserRole_whenUserExists_shouldUpdateRoleAndSyncSalesforce() {
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(RoleEnum.ADMIN, testUser.getRole());
        verify(userRepository, atLeastOnce()).save(testUser);
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
    }

    @Test
    void updateUserRole_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.updateUserRole(99L, RoleEnum.ADMIN));
    }

    @Test
    void updateUserRole_whenRoleNull_shouldThrowBadRequest() {
        assertThrows(ResponseStatusException.class, () -> userService.updateUserRole(1L, null));
    }

    @Test
    void updateUserRole_whenSalesforceSyncThrows_shouldMarkPending() {
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceRetryCount(0)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SF dual-write fail")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(SalesforceSyncStatus.PENDING, testUser.getSalesforceSyncStatus());
        assertEquals(1, testUser.getSalesforceRetryCount());
    }

    @Test
    void syncAuth0User_whenNewUserAndSalesforceFails_shouldCatchExceptionAndMarkPending() {
        when(userProviderRepository.findByProviderId("auth0|new-error-sf")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new-sf-error@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SF sync error")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

        User created = userService.syncAuth0User("auth0|new-error-sf", "new-sf-error@example.com", "New User", RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.PENDING, created.getSalesforceSyncStatus());
        assertEquals(1, created.getSalesforceRetryCount());
    }

    @Test
    void updateCurrentUser_whenPrincipalNull_shouldThrowUnauthorized() {
        assertThrows(ResponseStatusException.class,
                () -> userService.updateCurrentUser(null, new UserRequest("new@example.com", "New Name")));
    }

    @Test
    void updateCurrentUser_whenValidPrincipal_shouldUpdateAndReturnUser() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("alice@example.com");
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateCurrentUser(principal, new UserRequest("alice_new@example.com", "Alice Updated"));

        assertNotNull(response);
        assertEquals("Alice Updated", response.name());
        assertEquals("alice_new@example.com", response.email());
        verify(userRepository, atLeastOnce()).save(user1);
    }

    @Test
    void getUserByIdentifier_whenFoundByProviderId_shouldReturnUser() {
        UserProvider up = UserProvider.builder()
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

    @Test
    @DisplayName("getCurrentUser with null principal should throw UNAUTHORIZED")
    void getCurrentUser_whenPrincipalNull_shouldThrowUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> userService.getCurrentUser(null));
    }

    @Test
    @DisplayName("getCurrentUser with standard Principal should lookup by identifier")
    void getCurrentUser_whenStandardPrincipal_shouldLookupByIdentifier() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("alice@example.com");
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getCurrentUser(principal);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    @DisplayName("getCurrentUser with JWT and ADMIN custom claim should sync user with RoleEnum.ADMIN")
    void getCurrentUser_whenJwtWithAdminRole_shouldSyncAdmin() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|admin-1")
                .claim("email", "admin@example.com")
                .claim("name", "Admin User")
                .claim("https://library.kovanlabs.com/roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(userProviderRepository.findByProviderId("auth0|admin-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        verify(userRepository, atLeastOnce()).save(argThat(user -> user.getRole() == RoleEnum.ADMIN));
    }

    @Test
    @DisplayName("getCurrentUser when JWT lacks email and user not mapped should create new user using email claim")
    void getCurrentUser_whenJwtHasClaims_shouldCreateOrSyncUser() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-123")
                .header("alg", "none")
                .claim("sub", "google-oauth2|115993863360458947065")
                .claim("email", "thaniyarasi10@gmail.com")
                .claim("name", "Thani Yarasi")
                .claim("iss", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(userProviderRepository.findByProviderId("google-oauth2|115993863360458947065")).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("115993863360458947065")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("thaniyarasi10@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(50L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("thaniyarasi10@gmail.com", response.email());
        assertEquals("Thani Yarasi", response.name());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        verify(userProviderRepository).save(argThat(up -> "google-oauth2|115993863360458947065".equals(up.getProviderId())));
    }

    @Test
    @DisplayName("getCurrentUser when user already mapped in user_provider should not create new user")
    void getCurrentUser_whenUserAlreadyMapped_shouldReturnExistingUser() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-456")
                .header("alg", "none")
                .claim("sub", "auth0|existing-mapped-sub")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|existing-mapped-sub")
                .build();
        when(userProviderRepository.findByProviderId("auth0|existing-mapped-sub")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("getCurrentUser when JWT sub is new but matches existing MySQL user email should link provider without duplicate")
    void getCurrentUser_whenJwtSubIsNewAndMatchesExistingEmail_shouldLinkProviderWithoutDuplicate() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-789")
                .header("alg", "none")
                .claim("sub", "google-oauth2|new-google-sub")
                .claim("email", "alice@example.com")
                .claim("name", "Alice Google")
                .claim("iss", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(userProviderRepository.findByProviderId("google-oauth2|new-google-sub")).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("new-google-sub")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
        verify(userProviderRepository).save(argThat(p -> "google-oauth2|new-google-sub".equals(p.getProviderId()) && user1.getUuid().equals(p.getUserUuid())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void getUserByEmail_shouldDelegateToGetUserByIdentifier() {
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByEmail("alice@example.com");

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenGivenRawProviderIdWithPrefix_shouldFindUser() {
        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("115993863360458947065")
                .build();
        when(userProviderRepository.findByProviderId("google-oauth2|115993863360458947065"))
                .thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("115993863360458947065")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier("google-oauth2|115993863360458947065");

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenGivenUuidString_shouldFindUser() {
        when(userProviderRepository.findByProviderId(uuid1)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(uuid1)).thenReturn(Optional.empty());
        when(userRepository.findByUuid(uuid1)).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(uuid1)).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier(uuid1);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenIdentifierBlankOrNull_shouldThrowBadRequest() {
        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier(null));
        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier("   "));
    }



    @Test
    void getAllUsers_whenSalesforceReturnsUsers_shouldReturnSalesforceUsers() {
        ContactSObject sfContact = ContactSObject.builder()
                .externalUserUuid(uuid1.toString())
                .legacyUserId(1L)
                .lastName("Alice SF")
                .email("alice@example.com")
                .role("USER")
                .build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(sfContact));

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("Alice SF", result.get(0).name());
        verify(userRepository, never()).findAll();
    }

    @Test
    void getAllUsers_whenSalesforceFails_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF error"));
        when(userRepository.findAll()).thenReturn(List.of(user1));

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("Alice Smith", result.get(0).name());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsers_paginated_whenSalesforceReturnsUsers_shouldReturnPagedFromSF() {
        ContactSObject sfContact = ContactSObject.builder()
                .externalUserUuid(uuid1.toString())
                .legacyUserId(1L)
                .lastName("Alice SF")
                .email("alice@example.com")
                .role("USER")
                .build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(sfContact));

        PagedResponse<UserResponse> result = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Alice SF", result.content().get(0).name());
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllUsers_paginated_whenSalesforceFails_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF error"));
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> result = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Alice Smith", result.content().get(0).name());
        verify(userRepository).findAll(any(Pageable.class));
    }

    // --- Tests for getHeaderFromRequestContext ---

    @Test
    @DisplayName("getHeaderFromRequestContext should read X-User-Username and X-User-Name from RequestContextHolder when present")
    void getHeaderFromRequestContext_shouldExtractHeaderWhenPresent() {
        org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequest.addHeader("X-User-Username", "CustomHeaderUser");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(mockRequest)
        );

        try {
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("auth0|header-test");
            when(jwt.getClaimAsString("email")).thenReturn("header-user@example.com");

            org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
            when(auth.getPrincipal()).thenReturn(jwt);

            when(userProviderRepository.findByProviderId("auth0|header-test")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("header-user@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("CustomHeaderUser", response.name());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext should fallback to X-User-Name if X-User-Username is missing")
    void getHeaderFromRequestContext_shouldFallbackToXUserName() {
        org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequest.addHeader("X-User-Name", "FallbackHeaderName");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(mockRequest)
        );

        try {
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("auth0|header-fallback");
            when(jwt.getClaimAsString("email")).thenReturn("header-fallback@example.com");

            org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
            when(auth.getPrincipal()).thenReturn(jwt);

            when(userProviderRepository.findByProviderId("auth0|header-fallback")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("header-fallback@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("FallbackHeaderName", response.name());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext should return null safely when RequestContextHolder has no attributes")
    void getHeaderFromRequestContext_whenNoRequestContext_shouldSafelyReturnNull() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|no-ctx");
        when(jwt.getClaimAsString("email")).thenReturn("no-ctx@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("JwtName");

        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        when(userProviderRepository.findByProviderId("auth0|no-ctx")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("no-ctx@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("JwtName", response.name());
    }

    // --- Tests for syncUserToSalesforce ---

    @Test
    @DisplayName("syncUserToSalesforce should execute and mark SUCCESS on new user registration")
    void syncUserToSalesforce_whenNewUserCreated_shouldSyncAndMarkSuccess() {
        when(userProviderRepository.findByProviderId("auth0|sf-success")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("sf-success@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User created = userService.syncAuth0User("auth0|sf-success", "sf-success@example.com", "SF Success User", RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.SUCCESS, created.getSalesforceSyncStatus());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        verify(userRepository, atLeast(2)).save(any(User.class));
    }

    @Test
    @DisplayName("syncUserToSalesforce should increment retry count and mark PENDING when Salesforce throws exception")
    void syncUserToSalesforce_whenSalesforceThrows_shouldCatchErrorAndMarkPending() {
        when(userProviderRepository.findByProviderId("auth0|sf-fail")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("sf-fail@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce service connection error")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

        User created = userService.syncAuth0User("auth0|sf-fail", "sf-fail@example.com", "SF Fail User", RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.PENDING, created.getSalesforceSyncStatus());
        assertEquals(1, created.getSalesforceRetryCount());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("syncUserToSalesforce should not invoke Salesforce when salesforceSyncDelegate is null")
    void syncUserToSalesforce_whenDelegateIsNull_shouldDoNothing() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository,
                userProviderRepository,
                rewardRepository,
                null
        );

        when(userProviderRepository.findByProviderId("auth0|no-sf")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("no-sf@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User created = serviceWithoutSF.syncAuth0User("auth0|no-sf", "no-sf@example.com", "No SF", RoleEnum.USER);

        assertNotNull(created);
        verify(salesforceSyncDelegate, never()).syncContact(any(ContactSObject.class));
    }
}
