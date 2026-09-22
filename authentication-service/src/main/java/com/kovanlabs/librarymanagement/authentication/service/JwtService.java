package com.kovanlabs.librarymanagement.authentication.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import com.kovanlabs.librarymanagement.database.entity.User;

/**
 * Service for generating, signing, parsing, and validating JSON Web Tokens (JWT).
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;

    /**
     * Initializes the HMAC-SHA signing key from the configured Base64 secret.
     */
    @PostConstruct
    public void init() {
        signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
    }

    /**
     * Generates a signed JWT token from a Spring Security {@link Authentication} object.
     *
     * @param authentication The authenticated security context
     * @return Signed JWT compact string
     */
    public String generateToken(Authentication authentication) {

        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a signed JWT token for a specific {@link User} entity.
     *
     * @param user The user entity
     * @return Signed JWT compact string
     */
    public String generateToken(User user) {
        List<String> roles = List.of("ROLE_" + user.getRole().name());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts the subject (username/email) from the token.
     *
     * @param token The JWT string
     * @return The subject claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from a JWT using a resolver function.
     *
     * @param <T> The expected claim return type
     * @param token The JWT string
     * @param resolver Function to extract the claim from {@link Claims}
     * @return The extracted claim value
     */
    public <T> T extractClaim(String token,
                              Function<Claims, T> resolver) {

        Claims claims = extractAllClaims(token);

        return resolver.apply(claims);
    }

    /**
     * Parses and verifies all claims from the JWT string.
     *
     * @param token The JWT string
     * @return The parsed payload {@link Claims}
     */
    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates whether a token belongs to the given username and is not expired.
     *
     * @param token The JWT string
     * @param username The expected username
     * @return {@code true} if valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token,
                                String username) {

        String extractedUsername = extractUsername(token);

        return extractedUsername.equals(username)
                && !isTokenExpired(token);
    }

    /**
     * Checks if the token has expired.
     *
     * @param token The JWT string
     * @return {@code true} if expired, {@code false} otherwise
     */
    private boolean isTokenExpired(String token) {
        Date expiry = extractClaim(token, Claims::getExpiration);
        return expiry.before(new Date());
    }
}

