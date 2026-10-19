package com.example.clinic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

// Base class cho mọi Integration Test — tự dựng 1 container MySQL thật riêng cho lần chạy test,
// không đụng vào DB development. Container được giữ static (dùng chung cho mọi test class trong
// 1 lần chạy) để không phải khởi động lại MySQL hàng chục lần, giảm thời gian chạy test.
//@TestContainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Singleton container: khởi động 1 lần duy nhất cho cả lần chạy test, dùng chung cho mọi
    // test class. Không dùng @Container để tránh bị tắt/bật lại giữa các class
    // (Spring cache context sẽ trỏ vào container đã chết). Ryuk tự dọn container khi JVM thoát.
    //@Continers
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("clinic_test")
            .withUsername("test")
            .withPassword("test");
    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    // Dọn sạch toàn bộ bảng trước mỗi test method — mỗi test bắt đầu từ DB rỗng,
    // không bị dính dữ liệu của test trước (username/email unique...)
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = st.executeQuery(
                        "SELECT table_name FROM information_schema.tables " +
                                "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
                    while (rs.next()) tables.add(rs.getString(1));
                }
                for (String t : tables) st.execute("TRUNCATE TABLE `" + t + "`");
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    // Dọn sau mỗi test, theo thứ tự cố định:
    // 1. Chờ các listener @Async chạy xong, để chúng gọi mock/DB trước khi Spring reset mock
    //    và trước khi test kế tiếp truncate bảng.
    // 2. Dọn SecurityContext, tránh 1 test "giả lập đăng nhập" rồi còn sót sang test tiếp theo
    //    gây sai lệch ownership check không liên quan tới test đó.
    @AfterEach
    void cleanupAfterEach() {
        await().atMost(5, SECONDS).until(() ->
                asyncExecutor.getActiveCount() == 0
                        && asyncExecutor.getThreadPoolExecutor().getQueue().isEmpty());

        SecurityContextHolder.clearContext();
    }

}
