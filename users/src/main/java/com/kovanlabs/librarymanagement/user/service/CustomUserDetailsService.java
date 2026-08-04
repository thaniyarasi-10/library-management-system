package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {

        Users users = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Users not found"));

        return User //spring sec user
                .builder()
                .username(users.getEmail())
                .password(users.getPassword() != null ? users.getPassword() : "")
                .roles(users.getRole().name()) //name() is a enum method to give the value as string instead of enum
                .build();
    }
}
