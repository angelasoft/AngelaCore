package com.angelasoft.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 알림함에 저장된 알림을 받는 사람의 기기들로 푸시한다. 실패해도 알림함 저장에는 영향이 없다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSender {

    /** 앱(MainActivity)에서 만드는 안드로이드 알림 채널. */
    static final String ANDROID_CHANNEL_ID = "angela_default";
    private static final int MAX_TOKENS_PER_REQUEST = 500;
    private static final Set<MessagingErrorCode> DEAD_TOKEN_ERRORS =
            Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT);

    private final PushGateway gateway;
    private final PushTokenRepository tokenRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushRequested event) {
        if (!gateway.enabled() || event.userIds().isEmpty()) {
            return;
        }
        List<String> tokens = tokenRepository.findByUserIdIn(event.userIds()).stream()
                .map(PushToken::getToken)
                .toList();
        for (int from = 0; from < tokens.size(); from += MAX_TOKENS_PER_REQUEST) {
            send(tokens.subList(from, Math.min(from + MAX_TOKENS_PER_REQUEST, tokens.size())), event);
        }
    }

    private void send(List<String> tokens, PushRequested event) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(event.title()).setBody(event.body()).build())
                .putAllData(event.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder().setChannelId(ANDROID_CHANNEL_ID).build())
                        .build())
                .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setSound("default").build()).build())
                .build();
        try {
            BatchResponse response = gateway.messaging().sendEachForMulticast(message);
            List<String> deadTokens = new ArrayList<>();
            for (int i = 0; i < response.getResponses().size(); i++) {
                FirebaseMessagingException error = response.getResponses().get(i).getException();
                if (error != null && DEAD_TOKEN_ERRORS.contains(error.getMessagingErrorCode())) {
                    deadTokens.add(tokens.get(i));
                }
            }
            if (!deadTokens.isEmpty()) {
                tokenRepository.deleteByTokenIn(deadTokens);
            }
            log.info("[Push] 성공 {} / 실패 {} (만료 토큰 삭제 {})",
                    response.getSuccessCount(), response.getFailureCount(), deadTokens.size());
        } catch (FirebaseMessagingException e) {
            log.warn("[Push] 전송 실패: {}", e.getMessage());
        }
    }
}
