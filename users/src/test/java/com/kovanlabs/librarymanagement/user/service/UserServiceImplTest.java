package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.enums.AuthProvider;
import com.kovanlabs.librarymanagement.user.enums.RoleEnum;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private Users user1;
    private Users user2;

    @BeforeEach
    void setUp() {
        user1 = Users.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .provider(AuthProvider.USERNAME_PASSWORD)
                .build();

        user2 = Users.builder()
                .id(2L)
                .name("Bob")
                .email("bob@example.com")
                .role(RoleEnum.USER)
                .provider(AuthProvider.USERNAME_PASSWORD)
                .build();
    }

    @Test
    @DisplayName("getAllUsers with ascending order should return paginated content")
    void getAllUsers_AscendingOrder_ShouldReturnPagedResponse() {
        List<Users> usersList = List.of(user1, user2);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Users> usersPage = new PageImpl<>(usersList, pageable, usersList.size());

        when(userRepository.findAll(any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertEquals(0, response.pageNo());
        assertEquals(10, response.pageSize());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.last());
        assertEquals("Alice", response.content().get(0).name());
        assertEquals("Bob", response.content().get(1).name());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(10, capturedPageable.getPageSize());
        assertEquals(Sort.by("id").ascending(), capturedPageable.getSort());
    }

    @Test
    @DisplayName("getAllUsers with descending order should create descending sort pageable")
    void getAllUsers_DescendingOrder_ShouldReturnPagedResponse() {
        List<Users> usersList = List.of(user2, user1);
        Pageable pageable = PageRequest.of(0, 5, Sort.by("name").descending());
        Page<Users> usersPage = new PageImpl<>(usersList, pageable, 10);

        when(userRepository.findAll(any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 5, "name", "desc");

        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertEquals(0, response.pageNo());
        assertEquals(5, response.pageSize());
        assertEquals(10, response.totalElements());
        assertEquals(2, response.totalPages());
        assertFalse(response.last());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(Sort.by("name").descending(), capturedPageable.getSort());
    }

    @Test
    @DisplayName("getAllUsers with empty results should return empty PagedResponse")
    void getAllUsers_EmptyPage_ShouldReturnEmptyPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Users> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(userRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    @DisplayName("updateUser when user exists should update email and return UserResponse")
    void updateUser_WhenUserExists_ShouldUpdateAndReturnUserResponse() {
        UserRequest request = new UserRequest("updated@example.com", "newpassword123", "Alice Updated");
        Users updatedUser = Users.builder()
                .id(1L)
                .name("Alice")
                .email("updated@example.com")
                .role(RoleEnum.USER)
                .provider(AuthProvider.USERNAME_PASSWORD)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(Users.class))).thenReturn(updatedUser);

        UserResponse response = userService.updateUser(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("updated@example.com", response.email());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(user1);
        assertEquals("updated@example.com", user1.getEmail());
    }

    @Test
    @DisplayName("updateUser when user not found should throw ResponseStatusException NOT_FOUND")
    void updateUser_WhenUserNotFound_ShouldThrowResponseStatusException() {
        UserRequest request = new UserRequest("updated@example.com", "newpassword123", "Alice Updated");

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUser(99L, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Users not found with ID: 99"));
        verify(userRepository, times(1)).findById(99L);
        verify(userRepository, never()).save(any());
    }
}
