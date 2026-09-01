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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

        User user = userService.findOrCreateGoogleUser(
                googleId,
                email,
                name
        );

        String jwt = jwtService.generateToken(user);

        // Derive strict target origin from request
        String scheme = (request != null && request.getScheme() != null) ? request.getScheme() : "http";
        String serverName = (request != null && request.getServerName() != null) ? request.getServerName() : "localhost";
        int serverPort = (request != null) ? request.getServerPort() : 80;

        String targetOrigin;
        if (serverPort <= 0 || (scheme.equalsIgnoreCase("http") && serverPort == 80)
                || (scheme.equalsIgnoreCase("https") && serverPort == 443)) {
            targetOrigin = scheme + "://" + serverName;
        } else {
            targetOrigin = scheme + "://" + serverName + ":" + serverPort;
        }

        // Safely serialize message payload as JSON (preventing direct script string concatenation)
        Map<String, String> messagePayload = Map.of(
                "type", "ATHENAEUM_OAUTH_TOKEN",
                "token", jwt
        );

        String encodedRedirectUrl = "/index.html?token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8);

        // Escape JSON for safe embedding in HTML script block (neutralize any closing script tags)
        String jsonPayload = objectMapper.writeValueAsString(messagePayload).replace("</", "<\\/");
        String jsonOrigin = objectMapper.writeValueAsString(targetOrigin).replace("</", "<\\/");
        String jsonRedirectUrl = objectMapper.writeValueAsString(encodedRedirectUrl).replace("</", "<\\/");

        response.setContentType("text/html;charset=UTF-8");
        String html = "<!DOCTYPE html><html><head><title>Authentication Successful</title></head><body>" +
                "<script>" +
                "try {" +
                "  var payload = " + jsonPayload + ";" +
                "  var targetOrigin = " + jsonOrigin + " || window.location.origin;" +
                "  var redirectUrl = " + jsonRedirectUrl + ";" +
                "  if (window.opener && !window.opener.closed) {" +
                "    window.opener.postMessage(payload, targetOrigin);" +
                "    setTimeout(function() { window.close(); }, 300);" +
                "  } else {" +
                "    window.location.href = redirectUrl;" +
                "  }" +
                "} catch(e) {" +
                "  window.location.href = " + jsonRedirectUrl + ";" +
                "}" +
                "</script>" +
                "<p style='font-family:sans-serif; text-align:center; padding-top:40px; color:#666;'>Authentication completed. Redirecting to Athenaeum Library Hub...</p>" +
                "</body></html>";
        response.getWriter().write(html);
    }
}

