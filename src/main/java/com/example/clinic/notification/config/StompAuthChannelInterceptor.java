package com.example.clinic.notification.config;

import com.example.clinic.entity.User;
import com.example.clinic.security.JwtUtil;
import com.example.clinic.security.UserDetailServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

// Xác thực JWT ở frame CONNECT và khóa chặt những gì client được làm:
// chỉ SUBSCRIBE vào /user/queue/notifications, không được SEND gì lên server.
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ALLOWED_SUBSCRIPTION = "/user/queue/notifications";

    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        log.info("STOMP {} user={} dest={}", command, accessor.getUser(), accessor.getDestination());

        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            if (accessor.getUser() == null
                    || !ALLOWED_SUBSCRIPTION.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Không được phép subscribe destination này");
            }
        } else if (StompCommand.SEND.equals(command)) {
            throw new MessageDeliveryException("Client không được gửi message lên server");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Thiếu token");
        }
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new MessageDeliveryException("Token không hợp lệ");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(jwtUtil.extractEmail(token));
        if (!userDetails.isEnabled() || !jwtUtil.isTokenValid(token, userDetails)) {
            throw new MessageDeliveryException("Token không hợp lệ");
        }
        Long userId = ((User) userDetails).getId();
        // principal = userId (String) → getName() = userId, khớp convertAndSendToUser(userId, ...)
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, userDetails.getAuthorities());
    }
}
