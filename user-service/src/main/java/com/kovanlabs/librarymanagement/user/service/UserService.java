package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.database.entity.User;

import java.util.List;


/**
 * Service interface for User business logic, pagination, search, and authentication integrations.
 */
public interface UserService {

    /**
     * Creates a new user in the system.
     *
     * @param request The user creation request payload
     * @return The created {@link UserResponse} DTO
     */
    UserResponse createUser(UserRequest request);

    /**
     * Fetches all registered users.
     *
     * @return List of {@link UserResponse} DTOs
     */
    List<UserResponse> getAllUsers();

    /**
     * Fetches a paginated and sorted list of users.
     *
     * @param page Zero-based page number
     * @param size Number of items per page
     * @param sortBy Field to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} of {@link UserResponse}
     */
    PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir);

    /**
     * Searches users by query keyword with pagination.
     *
     * @param query Keyword to search for
     * @param page Zero-based page number
     * @param size Number of items per page
     * @param sortBy Field to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} of matching {@link UserResponse} records
     */
    PagedResponse<UserResponse> searchUsers(String query, int page, int size, String sortBy, String sortDir);

    /**
     * Retrieves a user by their database ID.
     *
     * @param id The user ID
     * @return The {@link UserResponse} DTO
     */
    UserResponse getUserById(Long id);

    /**
     * Retrieves a user by their email address.
     *
     * @param email The user email
     * @return The {@link UserResponse} DTO
     */
    UserResponse getUserByEmail(String email);

    /**
     * Updates an existing user's details.
     *
     * @param id The user ID
     * @param request The updated user payload
     * @return The updated {@link UserResponse} DTO
     */
    UserResponse updateUser(Long id, UserRequest request);

    /**
     * Deletes a user by their ID.
     *
     * @param id The user ID
     */
    void deleteUser(Long id);

    /**
     * Finds an existing user with the given Google OAuth ID / email or creates a new one.
     *
     * @param googleId The Google OAuth provider ID
     * @param email The user's Google email
     * @param name The user's display name
     * @return The resolved or newly created {@link User} entity
     */
    User findOrCreateGoogleUser(String googleId, String email, String name);
}
