package com.kovanlabs.librarymanagement.authentication.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.authentication.service.JwtService;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final @Lazy UserService userService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.origin}")
    private String frontendOrigin;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();

        User user = userService.findOrCreateGoogleUser(
                googleId,
                email,
                name
        );

        String jwt = jwtService.generateToken(user);

        Map<String, String> messagePayload = Map.of(
                "type", "ATHENAEUM_OAUTH_TOKEN",
                "token", jwt
        );

        String jsonPayload =
                objectMapper.writeValueAsString(messagePayload);

        response.setContentType("text/html;charset=UTF-8");

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Authentication Successful</title>
                </head>
                <body>
                    <script>
                        try {
                            const payload = %s;
                            const targetOrigin = %s;

                            if (window.opener && !window.opener.closed) {
                                window.opener.postMessage(
                                    payload,
                                    targetOrigin
                                );

                                setTimeout(() => {
                                    window.close();
                                }, 300);
                            } else {
                                window.location.replace(
                                    targetOrigin + "/"
                                );
                            }
                        } catch (e) {
                            window.location.replace(
                                %s + "/"
                            );
                        }
                    </script>

                    <p style="font-family:sans-serif;
                              text-align:center;
                              padding-top:40px;
                              color:#666;">
                        Authentication completed.
                        Redirecting to Athenaeum Library Hub...
                    </p>
                </body>
                </html>
                """.formatted(
                jsonPayload,
                objectMapper.writeValueAsString(frontendOrigin),
                objectMapper.writeValueAsString(frontendOrigin)
        );

        response.getWriter().write(html);
    }
}