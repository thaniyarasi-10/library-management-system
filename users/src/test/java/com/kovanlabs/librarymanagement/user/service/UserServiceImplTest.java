package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.service.BookService;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookService bookService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private Users user1;
    private Users user2;

    @BeforeEach
    void setUp() {
        user1 = Users.builder()
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .build();

        user2 = Users.builder()
                .id(2L)
                .name("Bob Jones")
                .email("bob@example.com")
                .build();
    }

    @Test
    @DisplayName("searchUsers should return PagedResponse with matching users")
    void searchUsers_ShouldReturnPagedResponse() {
        List<Users> users = List.of(user1);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Users> usersPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.searchUsers(eq("Alice"), any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.searchUsers("Alice", 0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(0, response.pageNo());
        assertEquals(10, response.pageSize());
        assertEquals(1, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.last());
        assertEquals("Alice Smith", response.content().get(0).name());
        assertEquals("alice@example.com", response.content().get(0).email());

        verify(userRepository, times(1)).searchUsers(eq("Alice"), any(Pageable.class));
    }
}
