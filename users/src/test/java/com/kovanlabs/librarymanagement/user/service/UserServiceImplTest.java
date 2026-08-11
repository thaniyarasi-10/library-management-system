package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
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

import java.util.Collections;
import java.util.List;

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
}
