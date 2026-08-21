package com.kovanlabs.librarymanagement.authentication.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.authentication.service.JwtService;
import com.kovanlabs.librarymanagement.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OidcUser oidcUser;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .providerId("google-sub-123")
                .provider(AuthProvider.GOOGLE_OAUTH)
                .role(RoleEnum.USER)
                .build();
    }

    @Test
    @DisplayName("onAuthenticationSuccess should extract OIDC claims, call findOrCreateGoogleUser, generate JWT, and write response")
    void onAuthenticationSuccess_Success() throws Exception {
        String googleId = "google-sub-123";
        String email = "test@example.com";
        String name = "Test User";
        String token = "mocked-jwt-token";

        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn(googleId);
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getFullName()).thenReturn(name);

        when(userService.findOrCreateGoogleUser(googleId, email, name)).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn(token);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(userService, times(1)).findOrCreateGoogleUser(googleId, email, name);
        verify(jwtService, times(1)).generateToken(mockUser);
        verify(response, times(1)).setContentType("application/json");
        verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);

        printWriter.flush();
        String jsonResponse = stringWriter.toString();
        assertTrue(jsonResponse.contains("\"token\":\"mocked-jwt-token\""));
    }
}

