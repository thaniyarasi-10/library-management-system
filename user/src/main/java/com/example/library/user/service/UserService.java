package com.example.library.user.service;

import com.example.library.user.dto.UserRequest;
import com.example.library.user.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserRequest request);
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse updateUser(Long id, UserRequest request);
    void deleteUser(Long id);
    UserResponse borrowBook(Long userId, Long bookId);
    UserResponse returnBook(Long userId, Long bookId);
}
