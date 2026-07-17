package com.example.library.user.dto;

import com.example.library.book.dto.BookResponse;
import java.util.List;

public record UserResponse(
    Long id,
    String name,
    String email,
    List<BookResponse> borrowedBooks
) {}
