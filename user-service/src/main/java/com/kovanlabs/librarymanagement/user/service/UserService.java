package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.database.entity.User;

import java.util.List;


public interface UserService {
    UserResponse createUser(UserRequest request);
    List<UserResponse> getAllUsers();
    PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir);
    PagedResponse<UserResponse> searchUsers(String query, int page, int size, String sortBy, String sortDir);
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    UserResponse updateUser(Long id, UserRequest request);
    void deleteUser(Long id);
    User findOrCreateGoogleUser(String googleId, String email, String name);
}
