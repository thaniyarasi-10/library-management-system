package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplOAuthTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private final String googleId = "google-sub-12345";
    private final String email = "oauthuser@example.com";
    private final String name = "OAuth User";

    @Test
    @DisplayName("findOrCreateGoogleUser creates new user with GOOGLE_OAUTH provider when email not found")
    void findOrCreateGoogleUser_WhenUserDoesNotExist_ShouldCreateNewGoogleUser() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateGoogleUser(googleId, email, name);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(name, result.getName());
        assertEquals(googleId, result.getProviderId());
        assertEquals(AuthProvider.GOOGLE_OAUTH, result.getProvider());
        assertEquals(RoleEnum.USER, result.getRole());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(email, savedUser.getEmail());
        assertEquals(googleId, savedUser.getProviderId());
        assertEquals(AuthProvider.GOOGLE_OAUTH, savedUser.getProvider());
    }

    @Test
    @DisplayName("findOrCreateGoogleUser links providerId and GOOGLE_OAUTH to existing user without providerId")
    void findOrCreateGoogleUser_WhenUserExistsWithoutProviderId_ShouldLinkGoogleProviderId() {
        User existingUser = User.builder()
                .id(10L)
                .email(email)
                .name("Existing User")
                .provider(AuthProvider.USERNAME_PASSWORD)
                .providerId(null)
                .role(RoleEnum.USER)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateGoogleUser(googleId, email, name);

        assertNotNull(result);
        assertEquals(googleId, result.getProviderId());
        assertEquals(AuthProvider.GOOGLE_OAUTH, result.getProvider());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("findOrCreateGoogleUser preserves existing providerId if already set")
    void findOrCreateGoogleUser_WhenUserAlreadyHasProviderId_ShouldReturnExistingUser() {
        User existingUser = User.builder()
                .id(10L)
                .email(email)
                .name("Existing User")
                .provider(AuthProvider.GOOGLE_OAUTH)
                .providerId("existing-provider-id")
                .role(RoleEnum.USER)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateGoogleUser(googleId, email, name);

        assertNotNull(result);
        assertEquals("existing-provider-id", result.getProviderId());
        assertEquals(AuthProvider.GOOGLE_OAUTH, result.getProvider());
        verify(userRepository, times(1)).save(existingUser);
    }
}


