package com.example.clinic.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

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

                // Thêm đoạn này
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of(
                            "http://localhost:5500",
                            "http://127.0.0.1:5500",
                            "http://localhost:5173",
                            "http://127.0.0.1:5173"
                    ));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    return config;
                }))

                // Cấu hình phân quyền từng API
                .authorizeHttpRequests(auth -> auth

                        // API công khai — không cần token
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/google",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()

                        .requestMatchers("/api/v1/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/auth/logout").authenticated()

                        // API chỉ ADMIN được dùng
                        .requestMatchers("/api/v1/dashboard/**")
                        .hasRole("ADMIN")

                        // API quản lý user chỉ dành cho ADMIN
                        .requestMatchers("/api/v1/users/**")
                        .hasRole("ADMIN")

                        // Bệnh nhân xem hồ sơ của chính mình
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/me")
                        .hasRole("PATIENT")

                        // Admin xem hồ sơ bệnh nhân theo userId
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/user/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST")

                        // Xem chi tiết bệnh nhân
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR")

                        // Tạo / cập nhật / xóa bệnh nhân
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/patients/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/patients/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST")

                        // Bác sĩ xem hồ sơ của chính mình
                        .requestMatchers(HttpMethod.GET, "/api/v1/doctors/me")
                        .hasRole("DOCTOR")

                        .requestMatchers(HttpMethod.GET, "/api/v1/doctors/user/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST")

                        // Xem thông tin bác sĩ
                        .requestMatchers(HttpMethod.GET, "/api/v1/doctors/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // CRUD bác sĩ
                        .requestMatchers(HttpMethod.POST, "/api/v1/doctors/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/doctors/**")
                        .hasAnyRole("ADMIN", "DOCTOR")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/doctors/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/v1/specialties/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        .requestMatchers("/api/v1/specialties/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/v1/appointments/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // Xem lịch làm việc
                        .requestMatchers(HttpMethod.GET, "/api/v1/doctor-schedules/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // Quản lý lịch làm việc
                        .requestMatchers("/api/v1/doctor-schedules/**")
                        .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR")

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
