package com.kovanlabs.librarymanagement.authentication.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.authentication.service.JwtService;
import com.kovanlabs.librarymanagement.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final @Lazy UserService userService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();

        User User = userService.findOrCreateGoogleUser(
                googleId,
                email,
                name
        );

        String jwt = jwtService.generateToken(User);

        response.setContentType("text/html;charset=UTF-8");
        String html = "<!DOCTYPE html><html><head><title>Authentication Successful</title></head><body>" +
                "<script>" +
                "try {" +
                "  if (window.opener && !window.opener.closed) {" +
                "    window.opener.postMessage({ type: 'ATHENAEUM_OAUTH_TOKEN', token: '" + jwt + "' }, '*');" +
                "    setTimeout(function() { window.close(); }, 300);" +
                "  } else {" +
                "    window.location.href = '/index.html?token=" + jwt + "';" +
                "  }" +
                "} catch(e) {" +
                "  window.location.href = '/index.html?token=" + jwt + "';" +
                "}" +
                "</script>" +
                "<p style='font-family:sans-serif; text-align:center; padding-top:40px; color:#666;'>Authentication completed. Redirecting to Athenaeum Library Hub...</p>" +
                "</body></html>";
        response.getWriter().write(html);
    }
}

