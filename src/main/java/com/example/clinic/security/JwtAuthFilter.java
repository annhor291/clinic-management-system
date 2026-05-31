package com.example.clinic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Filter chạy 1 lần mỗi request
// Kiểm tra JWT token trong header Authorization
// Nếu token hợp lệ → set Authentication vào SecurityContext
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Lấy token từ header Authorization
            String token = extractTokenFromRequest(request);

            // Nếu có token và token hợp lệ
            if (token != null && jwtUtil.validateToken(token)) {
                // Lấy email từ token
                String email = jwtUtil.extractEmail(token);

                // Load thông tin user từ DB
                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                // Kiểm tra token có khớp với user không
                if (jwtUtil.isTokenValid(token, userDetails)) {
                    // Tạo Authentication object và set vào SecurityContext
                    // Từ đây Spring Security biết request này đã được xác thực
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Không thể set authentication: {}", e.getMessage());
        }

        // Tiếp tục chuỗi filter
        filterChain.doFilter(request, response);
    }

    // Lấy token từ header Authorization
    // Format: "Bearer <token>"
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Bỏ "Bearer " lấy token
        }
        return null;
    }
}
