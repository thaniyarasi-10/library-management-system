package com.kovanlabs.librarymanagement.user.controller;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    @DisplayName("GET /users with default query params should return 200 OK and PagedResponse")
    void getAllUsers_WithDefaultParams_ShouldReturnPagedUsers() throws Exception {
        UserResponse u1 = new UserResponse(1L, "Alice", "alice@example.com");
        UserResponse u2 = new UserResponse(2L, "Bob", "bob@example.com");
        PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                List.of(u1, u2), 0, 10, 2L, 1, true
        );

        when(userService.getAllUsers(0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Alice"))
                .andExpect(jsonPath("$.content[1].name").value("Bob"))
                .andExpect(jsonPath("$.pageNo").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        verify(userService, times(1)).getAllUsers(0, 10, "id", "asc");
    }

    @Test
    @DisplayName("GET /users with custom pagination params should pass parameters to service")
    void getAllUsers_WithCustomParams_ShouldPassParamsToService() throws Exception {
        UserResponse u1 = new UserResponse(3L, "Charlie", "charlie@example.com");
        PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                List.of(u1), 1, 5, 6L, 2, false
        );

        when(userService.getAllUsers(1, 5, "name", "desc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/users")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortBy", "name")
                        .param("sortDir", "desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Charlie"))
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        verify(userService, times(1)).getAllUsers(1, 5, "name", "desc");
    }

    @Test
    @DisplayName("PUT /users/{id} with valid payload should return 200 OK and updated UserResponse")
    void updateUser_WithValidPayload_ShouldReturnUpdatedUser() throws Exception {
        UserResponse response = new UserResponse(1L, "Alice Updated", "updated@example.com");

        when(userService.updateUser(eq(1L), any(UserRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "email": "updated@example.com",
                    "password": "password123",
                    "name": "Alice Updated"
                }
                """;

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(userService, times(1)).updateUser(eq(1L), any(UserRequest.class));
    }

    @Test
    @DisplayName("PUT /users/{id} when user not found should return 404 NOT_FOUND")
    void updateUser_WhenUserNotFound_ShouldReturn404() throws Exception {
        when(userService.updateUser(eq(99L), any(UserRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Users not found with ID: 99"));

        String jsonPayload = """
                {
                    "email": "updated@example.com",
                    "password": "password123",
                    "name": "Alice Updated"
                }
                """;

        mockMvc.perform(put("/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).updateUser(eq(99L), any(UserRequest.class));
    }

    @Test
    @DisplayName("PUT /users/{id} with invalid email format should return 400 Bad Request")
    void updateUser_WithInvalidEmail_ShouldReturn400BadRequest() throws Exception {
        String invalidJsonPayload = """
                {
                    "email": "not-an-email",
                    "password": "password123",
                    "name": "Alice Updated"
                }
                """;

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJsonPayload))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any(), any());
    }
}

