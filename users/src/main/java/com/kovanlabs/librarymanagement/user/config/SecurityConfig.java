package com.kovanlabs.librarymanagement.user.config;

import com.kovanlabs.librarymanagement.user.filter.JwtAuthenticationFilter;
import com.kovanlabs.librarymanagement.user.oauth.OAuth2AuthenticationSuccessHandler;
import com.kovanlabs.librarymanagement.user.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity

public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final OAuth2AuthenticationSuccessHandler successHandler;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return  new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/users")
                        .permitAll()

                        .requestMatchers(HttpMethod.GET, "/books/**")
                        .permitAll()

                        .requestMatchers(HttpMethod.POST, "/books/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/books/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE, "/books/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/borrow/**")
                        .hasAnyRole("USER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/users/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/users/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE, "/users/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST,"/auth/login")
                        .permitAll()

                        .requestMatchers("/error").permitAll()

                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth.successHandler(successHandler))
                .authenticationProvider(authenticationProvider(userDetailsService, passwordEncoder()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception{
        return authenticationConfiguration.getAuthenticationManager(); //returns already created ProviderManager
    }


    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService customUserDetailsService, PasswordEncoder passwordEncoder){

        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(customUserDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);

        return daoAuthenticationProvider;

    }

}
