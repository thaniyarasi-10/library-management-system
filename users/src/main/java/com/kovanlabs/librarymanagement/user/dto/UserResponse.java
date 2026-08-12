package com.kovanlabs.librarymanagement.user.dto;

import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import java.util.List;

public record UserResponse(
    Long id,
    String name,
    String email
) {}
