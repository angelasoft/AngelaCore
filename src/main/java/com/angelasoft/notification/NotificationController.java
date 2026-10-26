package com.angelasoft.notification;

import com.angelasoft.admin.security.AdminUserDetails;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 알림함 API (인증 필요).
 *
 *   GET  /app/notifications                최근 알림 50건
 *   GET  /app/notifications/unread-count   안 읽은 알림 수
 *   POST /app/notifications/{id}/read      읽음 처리
 *   POST /app/notifications/read-all       모두 읽음 처리
 */
@RestController
@RequestMapping("/app/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AdminUserDetails principal) {
        return notificationService.recent(principal.getUserId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AdminUserDetails principal) {
        return Map.of("count", notificationService.unreadCount(principal.getUserId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable Long id) {
        if (!notificationService.markRead(principal.getUserId(), id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "알림을 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AdminUserDetails principal) {
        return Map.of("updated", notificationService.markAllRead(principal.getUserId()));
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String title,
            String message,
            Long targetId,
            boolean read,
            Instant createdAt
    ) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getTargetId(), n.isRead(),
                    n.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
        }
    }
}
