package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.service.BookService;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

    @Service
    @RequiredArgsConstructor
    @Transactional(readOnly = true)
    public class UserServiceImpl implements UserService {

        private final UserRepository userRepository;
        private final BookService bookService;
        private final PasswordEncoder passwordEncoder;

        @Override
        @Transactional
        public UserResponse createUser(UserRequest request) {
            Users users = Users.builder()
                    .email(request.email())
                    .password(passwordEncoder.encode(request.password()))
                    .name(request.name())
                    .build();
            Users savedUsers = userRepository.save(users);
            return mapToResponse(savedUsers);
        }

        @Override
        public List<UserResponse> getAllUsers() {
            return userRepository.findAll().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        @Override
        public UserResponse getUserById(Long id) {
            Users users = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Users not found with ID: " + id));
            return mapToResponse(users);
        }

        @Override
        @Transactional
        public UserResponse updateUser(Long id, UserRequest request) {
            Users users = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Users not found with ID: " + id));

            users.setEmail(request.email());

            Users updatedUsers = userRepository.save(users);
            return mapToResponse(updatedUsers);
        }

        @Override
        @Transactional
        public void deleteUser(Long id) {
            if (!userRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Users not found with ID: " + id);
            }
            userRepository.deleteById(id);
        }


        private UserResponse mapToResponse(Users users) {

            return new UserResponse(
                    users.getId(),
                    users.getName(),
                    users.getEmail()
            );
        }
    }
