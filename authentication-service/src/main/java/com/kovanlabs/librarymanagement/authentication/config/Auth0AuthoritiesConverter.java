package com.kovanlabs.librarymanagement.authentication.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Auth0AuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String DEFAULT_ROLES_CLAIM = "https://library.kovanlabs.com/roles";
    public static final String PERMISSIONS_CLAIM = "permissions";
    public static final String STANDARD_ROLES_CLAIM = "roles";

    private final String customRolesClaim;

    public Auth0AuthoritiesConverter() {
        this(DEFAULT_ROLES_CLAIM);
    }

    public Auth0AuthoritiesConverter(String customRolesClaim) {
        this.customRolesClaim = (customRolesClaim != null && !customRolesClaim.isBlank())
                ? customRolesClaim
                : DEFAULT_ROLES_CLAIM;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Extract permissions (Auth0 RBAC)
        List<String> permissions = extractStringList(jwt, PERMISSIONS_CLAIM);
        for (String permission : permissions) {
            if (!permission.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }

        // 2. Extract roles from configured namespaced claim
        List<String> namespacedRoles = extractStringList(jwt, customRolesClaim);
        for (String role : namespacedRoles) {
            addRoleAuthority(authorities, role);
        }

        // 3. Fallback to standard roles claim if configured claim returned none
        if (namespacedRoles.isEmpty() && !customRolesClaim.equals(STANDARD_ROLES_CLAIM)) {
            List<String> standardRoles = extractStringList(jwt, STANDARD_ROLES_CLAIM);
            for (String role : standardRoles) {
                addRoleAuthority(authorities, role);
            }
        }

        return new ArrayList<>(authorities);
    }

    private void addRoleAuthority(Set<GrantedAuthority> authorities, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        String trimmed = role.trim();
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("ROLE_")) {
            authorities.add(new SimpleGrantedAuthority(upper));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + upper));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        }
        return Collections.emptyList();
    }
}
