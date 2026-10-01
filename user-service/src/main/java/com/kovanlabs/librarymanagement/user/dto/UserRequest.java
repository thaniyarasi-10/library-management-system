package com.kovanlabs.librarymanagement.user.dto;

import com.kovanlabs.librarymanagement.user.validation.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    String email,

    String name,

    @ValidPhoneNumber(
        message = "Invalid mobile number. Please enter a valid mobile number with country code"
    )
    String phone
) {
    public UserRequest(String email, String name) {
        this(email, name, null);
    }
}

