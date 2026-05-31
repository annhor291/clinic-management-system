package com.example.clinic.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Cho phép dùng @PreAuthorize trên Controller/Service
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
                // Tắt CSRF vì dùng JWT (stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Cấu hình phân quyền từng API
                .authorizeHttpRequests(auth -> auth

                        // API công khai — không cần token
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // API chỉ ADMIN được dùng
                        .requestMatchers("/api/v1/dashboard/**")
                        .hasRole("ADMIN")

                        // API ADMIN + RECEPTIONIST
                        .requestMatchers("/api/v1/patients/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "PATIENT")
                        .requestMatchers("/api/v1/doctors/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")
                        .requestMatchers("/api/v1/specialties/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // API đặt lịch
                        .requestMatchers("/api/v1/appointments/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")
                        .requestMatchers("/api/v1/doctor-schedules/**")
                        .hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/v1/time-slots/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // Tất cả request còn lại cần xác thực
                        .anyRequest().authenticated()
                )

                // Stateless — không dùng session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Thêm authentication provider
                .authenticationProvider(authenticationProvider())

                // Thêm JWT filter trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Mã hóa password bằng BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationProvider: dùng DB để xác thực
    // Mới - Spring Boot 4.x
    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    // AuthenticationManager: dùng trong AuthService để đăng nhập
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
