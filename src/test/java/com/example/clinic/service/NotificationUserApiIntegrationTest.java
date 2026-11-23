package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.response.NotificationResponse;
import com.example.clinic.entity.Notification;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.NotificationType;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.NotificationRepository;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NotificationUserApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserNotificationService userNotificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private User newUser(String name) {
        return userRepository.save(User.builder()
                .username(name).email(name + "@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private Notification newNotification(User user, String title, boolean read) {
        return notificationRepository.save(Notification.builder()
                .user(user).type(NotificationType.APPOINTMENT_BOOKED)
                .title(title).content("noi dung").read(read).build());
    }

    @Test
    void list_shouldReturnOnlyOwnNotificationsNewestFirst() throws InterruptedException {
        User me = newUser("notif_me");
        User other = newUser("notif_other");
        newNotification(me, "cu", true);
        Thread.sleep(10);
        newNotification(me, "moi", false);
        newNotification(other, "cua nguoi khac", false);

        Page<NotificationResponse> page = userNotificationService.list(me.getId(), false, 0, 20);

        assertThat(page.getContent()).extracting(NotificationResponse::title)
                .containsExactly("moi", "cu");
    }

    @Test
    void list_unreadOnly_shouldFilterReadOnes() {
        User me = newUser("notif_unread");
        newNotification(me, "da doc", true);
        newNotification(me, "chua doc", false);

        Page<NotificationResponse> page = userNotificationService.list(me.getId(), true, 0, 20);

        assertThat(page.getContent()).extracting(NotificationResponse::title)
                .containsExactly("chua doc");
    }

    @Test
    void list_sizeAboveLimit_shouldBeCappedAt50() {
        User me = newUser("notif_cap");
        newNotification(me, "a", false);

        Page<NotificationResponse> page = userNotificationService.list(me.getId(), false, 0, 9999);

        assertThat(page.getSize()).isEqualTo(50);
    }

    @Test
    void countUnread_shouldCountOnlyOwnUnread() {
        User me = newUser("notif_count");
        User other = newUser("notif_count_other");
        newNotification(me, "1", false);
        newNotification(me, "2", false);
        newNotification(me, "3", true);
        newNotification(other, "4", false);

        assertThat(userNotificationService.countUnread(me.getId())).isEqualTo(2);
    }

    @Test
    void markRead_ownNotification_shouldSetRead() {
        User me = newUser("notif_markread");
        Notification n = newNotification(me, "x", false);

        userNotificationService.markRead(me.getId(), n.getId());

        assertThat(notificationRepository.findById(n.getId()).orElseThrow().isRead()).isTrue();
    }

    @Test
    void markRead_otherUsersNotification_shouldThrowNotFoundAndKeepUnread() {
        User me = newUser("notif_mr_me");
        User other = newUser("notif_mr_other");
        Notification n = newNotification(other, "cua nguoi khac", false);

        assertThatThrownBy(() -> userNotificationService.markRead(me.getId(), n.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(notificationRepository.findById(n.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void markAllRead_shouldAffectOnlyOwnNotifications() {
        User me = newUser("notif_all_me");
        User other = newUser("notif_all_other");
        newNotification(me, "1", false);
        newNotification(me, "2", false);
        newNotification(other, "3", false);

        int updated = userNotificationService.markAllRead(me.getId());

        assertThat(updated).isEqualTo(2);
        assertThat(userNotificationService.countUnread(me.getId())).isZero();
        assertThat(userNotificationService.countUnread(other.getId())).isEqualTo(1);
    }
}
