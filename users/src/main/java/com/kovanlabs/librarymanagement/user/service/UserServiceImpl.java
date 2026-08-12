package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.service.BookService;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.librarymanagement.user.enums.AuthProvider;
import com.kovanlabs.librarymanagement.user.enums.RoleEnum;

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
        public PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir) {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Users> usersPage = userRepository.findAll(pageable);
            List<UserResponse> content = usersPage.getContent().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            return new PagedResponse<>(
                    content,
                    usersPage.getNumber(),
                    usersPage.getSize(),
                    usersPage.getTotalElements(),
                    usersPage.getTotalPages(),
                    usersPage.isLast()
            );
        }

        @Override
        public PagedResponse<UserResponse> searchUsers(String query, int page, int size, String sortBy, String sortDir) {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Users> usersPage = userRepository.searchUsers(query, pageable);
            List<UserResponse> content = usersPage.getContent().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            return new PagedResponse<>(
                    content,
                    usersPage.getNumber(),
                    usersPage.getSize(),
                    usersPage.getTotalElements(),
                    usersPage.getTotalPages(),
                    usersPage.isLast()
            );
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

        @Override
        @Transactional
        public Users findOrCreateGoogleUser(String googleId, String email, String name) {
            return userRepository.findByEmail(email)
                    .map(existingUser -> {
                        if (existingUser.getProviderId() == null) {
                            existingUser.setProviderId(googleId);
                            existingUser.setProvider(AuthProvider.GOOGLE_OAUTH);
                        }
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> {
                        Users newUser = Users.builder()
                                .email(email)
                                .name(name)
                                .providerId(googleId)
                                .provider(AuthProvider.GOOGLE_OAUTH)
                                .role(RoleEnum.USER)
                                .build();
                        return userRepository.save(newUser);
                    });
        }


        private UserResponse mapToResponse(Users users) {

            return new UserResponse(
                    users.getId(),
                    users.getName(),
                    users.getEmail()
            );
        }
    }
