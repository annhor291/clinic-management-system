package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.response.NotificationResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.notification.event.NotificationCreatedEvent;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Cần cổng thật để WebSocket client kết nối -> ghi đè webEnvironment của BaseIntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class NotificationWebSocketIntegrationTest extends BaseIntegrationTest {

    @Value("${local.server.port}") private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private org.springframework.messaging.simp.user.SimpUserRegistry simpUserRegistry;

    private final List<WebSocketStompClient> clients = new ArrayList<>();


    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
    }

    private User newUser(String name) {
        return userRepository.save(User.builder()
                .username(name).email(name + "@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        // Server gửi content-type application/json; StringMessageConverter mặc định chỉ nhận text/plain
        // nên client bỏ qua frame. Cho phép thêm application/json để đọc payload dạng chuỗi.
        client.setMessageConverter(new org.springframework.messaging.converter.StringMessageConverter() {{
            addSupportedMimeTypes(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        }});
        clients.add(client);
        return client;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) headers.add("Authorization", "Bearer " + token);
        return newClient().connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                        System.out.println("STOMP CLIENT EXCEPTION: " + ex);
                    }
                    @Override
                    public void handleTransportError(StompSession s, Throwable ex) {
                        System.out.println("STOMP CLIENT TRANSPORT ERROR: " + ex);
                    }
                }
        ).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> subscribe(StompSession session) throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        });
        Thread.sleep(500); // SUBSCRIBE được xử lý bất đồng bộ phía server -> chờ đăng ký xong
        return received;
    }

    // Publish trong transaction để @TransactionalEventListener(AFTER_COMMIT) được kích hoạt
    private void publishFor(User user, String title) {
        NotificationResponse dto = new NotificationResponse(
                1L, "APPOINTMENT_BOOKED", title, "noi dung", null, false, LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new NotificationCreatedEvent(user.getId(), dto)));
    }

    @Test
    void connect_withoutToken_shouldBeRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void connect_withInvalidToken_shouldBeRejected() {
        assertThatThrownBy(() -> connect("khong.phai.jwt"))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void subscribe_shouldReceiveNotificationAfterCommit() throws Exception {
        User me = newUser("ws_me");
        StompSession session = connect(jwtUtil.generateToken(me));
        BlockingQueue<String> received = subscribe(session);

        System.out.println("REGISTRY user=" + simpUserRegistry.getUser(String.valueOf(me.getId()))
                + " | all=" + simpUserRegistry.getUsers());

        String payload = publishUntilReceived(me, "Dat lich thanh cong", received);
        assertThat(payload).isNotNull().contains("Dat lich thanh cong");
    }

    @Test
    void notification_shouldNotReachOtherUsers() throws Exception {
        User me = newUser("ws_owner");
        User other = newUser("ws_stranger");
        BlockingQueue<String> mine = subscribe(connect(jwtUtil.generateToken(me)));
        BlockingQueue<String> theirs = subscribe(connect(jwtUtil.generateToken(other)));

        assertThat(publishUntilReceived(me, "Rieng tu cua me", mine)).isNotNull();
        assertThat(theirs.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void notification_shouldNotBePushedWhenTransactionRollsBack() throws Exception {
        User me = newUser("ws_rollback");
        BlockingQueue<String> received = subscribe(connect(jwtUtil.generateToken(me)));

        NotificationResponse dto = new NotificationResponse(
                1L, "APPOINTMENT_BOOKED", "Khong duoc gui", "x", null, false, LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new NotificationCreatedEvent(me.getId(), dto));
            status.setRollbackOnly();
        });

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    // Gửi lặp tới khi có tin về: tránh phụ thuộc thời điểm server đăng ký xong SUBSCRIBE
    private String publishUntilReceived(User user, String title, BlockingQueue<String> queue) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            publishFor(user, title);
            String payload = queue.poll(500, TimeUnit.MILLISECONDS);
            if (payload != null) return payload;
        }
        return null;
    }
}
