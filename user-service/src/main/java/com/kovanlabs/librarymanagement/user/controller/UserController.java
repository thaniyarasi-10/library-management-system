package com.kovanlabs.librarymanagement.user.controller;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import com.kovanlabs.librarymanagement.database.entity.User;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserRequest request){
        return userService.createUser(request);
    }

    @GetMapping
    public PagedResponse<UserResponse> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir) {

        return userService.getAllUsers(page, size, sortBy, sortDir);
    }

    @GetMapping("/search")
    public PagedResponse<UserResponse> searchUsers(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size,
            @RequestParam(name = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc", required = false) String sortDir) {
        return userService.searchUsers(query, page, size, sortBy, sortDir);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(java.security.Principal principal) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (email == null) {
                email = jwt.getClaimAsString("https://library.kovanlabs.com/email");
            }
            String name = jwt.getClaimAsString("name");
            if (name == null) {
                name = jwt.getClaimAsString("nickname");
            }

            User syncedUser = userService.syncAuth0User(sub, email, name);
            return userService.getUserByIdentifier(sub);
        }

        return userService.getUserByIdentifier(principal.getName());
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable("id") Long id) {
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable("id") Long id, @Valid @RequestBody UserRequest request) {
        return userService.updateUser(id, request);
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody com.kovanlabs.librarymanagement.user.dto.UserRoleUpdateRequest request) {
        return userService.updateUserRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
    }

}
