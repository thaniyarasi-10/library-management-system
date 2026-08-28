package com.kovanlabs.librarymanagement.authentication.controller;

import com.kovanlabs.librarymanagement.authentication.dto.LoginResponse;
import com.kovanlabs.librarymanagement.authentication.service.JwtService;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public LoginResponse login (@RequestBody @Valid UserRequest userRequest){
        Authentication authentication= authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userRequest.email(), userRequest.password())
        );

        String token = jwtService.generateToken(authentication);
        return new LoginResponse(token);
    }
}
