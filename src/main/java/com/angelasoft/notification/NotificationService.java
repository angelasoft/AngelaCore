package com.angelasoft.notification;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** 받는 사람마다 알림을 한 건씩 저장한다. 호출한 트랜잭션에 함께 묶인다. */
    @Transactional
    public void notify(Collection<Long> recipientUserIds, NotificationType type, String title, String message, Long targetId) {
        List<Notification> notifications = recipientUserIds.stream()
                .distinct()
                .map(userId -> Notification.builder()
                        .recipientUserId(userId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .targetId(targetId)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
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
