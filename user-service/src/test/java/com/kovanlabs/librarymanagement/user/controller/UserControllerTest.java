package com.kovanlabs.librarymanagement.user.controller;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
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
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private UUID uuid1;
    private UUID uuid2;
    private Long id1;
    private Long id2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        id1 = 1L;
        id2 = 2L;
    }

    @Test
    @DisplayName("GET /user with default query params should return 200 OK and PagedResponse")
    void getAllUsers_WithDefaultParams_ShouldReturnPagedUsers() throws Exception {
        UserResponse u1 = new UserResponse(uuid1, id1, "Alice", "alice@example.com");
        UserResponse u2 = new UserResponse(uuid2, id2, "Bob", "bob@example.com");
        PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                List.of(u1, u2), 0, 10, 2L, 1, true
        );

        when(userService.getAllUsers(0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/user")
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
    @DisplayName("PUT /user/{id} with valid payload should return 200 OK and updated UserResponse")
    void updateUser_WithValidPayload_ShouldReturnUpdatedUser() throws Exception {
        UserResponse response = new UserResponse(uuid1, id1, "Alice Updated", "updated@example.com");

        when(userService.updateUser(eq(id1), any(UserRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "email": "updated@example.com",
                    "password": "password123",
                    "name": "Alice Updated"
                }
                """;

        mockMvc.perform(put("/user/" + id1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(userService, times(1)).updateUser(eq(id1), any(UserRequest.class));
    }

    @Test
    @DisplayName("DELETE /user/{id} when user exists should return 204 No Content")
    void deleteUser_WhenUserExists_ShouldReturn204NoContent() throws Exception {
        doNothing().when(userService).deleteUser(id1);

        mockMvc.perform(delete("/user/" + id1))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(id1);
    }

    @Test
    @DisplayName("POST /user should create and return user")
    void createUser_ShouldReturnCreatedUser() throws Exception {
        UserResponse response = new UserResponse(uuid1, id1, "Alice", "alice@example.com");
        when(userService.createUser(any(UserRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "email": "alice@example.com",
                    "password": "Password123!",
                    "name": "Alice"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @DisplayName("GET /user/{id} should return user by id")
    void getUserById_ShouldReturnUser() throws Exception {
        UserResponse response = new UserResponse(uuid1, id1, "Alice", "alice@example.com");
        when(userService.getUserById(id1)).thenReturn(response);

        mockMvc.perform(get("/user/" + id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @DisplayName("GET /user/search should return paged users")
    void searchUsers_ShouldReturnPagedUsers() throws Exception {
        UserResponse response = new UserResponse(uuid1, id1, "Alice", "alice@example.com");
        PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                List.of(response), 0, 10, 1L, 1, true
        );
        when(userService.searchUsers("Alice", 0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/user/search").param("query", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"));
    }

    @Test
    @DisplayName("GET /user/me should return current user profile")
    void getCurrentUser_ShouldReturnProfile() throws Exception {
        UserResponse response = new UserResponse(uuid1, id1, "Alice", "alice@example.com");
        java.security.Principal mockPrincipal = mock(java.security.Principal.class);
        when(mockPrincipal.getName()).thenReturn("alice@example.com");
        when(userService.getUserByEmail("alice@example.com")).thenReturn(response);

        mockMvc.perform(get("/user/me").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }
}
