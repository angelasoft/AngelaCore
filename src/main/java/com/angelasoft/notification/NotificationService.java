package com.angelasoft.notification;

import com.angelasoft.notification.push.PushRequested;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 받는 사람마다 알림을 한 건씩 저장한다. 호출한 트랜잭션에 함께 묶이고,
     * 커밋되면 같은 내용을 받는 사람들의 기기로 푸시한다.
     */
    @Transactional
    public void notify(Collection<Long> recipientUserIds, NotificationType type, String title, String message, Long targetId) {
        List<Long> userIds = recipientUserIds.stream().distinct().toList();
        List<Notification> notifications = userIds.stream()
                .map(userId -> Notification.builder()
                        .recipientUserId(userId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .targetId(targetId)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);

        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (targetId != null) {
            data.put("targetId", targetId.toString());
        }
        eventPublisher.publishEvent(new PushRequested(userIds, title, message, data));
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(Long userId) {
        return notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    @Transactional
    public boolean markRead(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .map(n -> {
                    n.markRead();
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }
}
