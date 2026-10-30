package com.angelasoft.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 서비스 계정 키가 있을 때만 FirebaseMessaging 을 만든다. 없으면(로컬·테스트) 푸시 없이 앱 알림함만 쓴다. */
@Slf4j
@EnableAsync
@Configuration
public class FirebaseConfig {

    @Bean
    PushGateway pushGateway(@Value("${app.firebase.service-account-base64:}") String serviceAccountBase64)
            throws IOException {
        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            log.info("[Push] FIREBASE_SERVICE_ACCOUNT_BASE64 가 없어 푸시 알림을 보내지 않습니다.");
            return PushGateway.disabled();
        }
        byte[] json = Base64.getDecoder().decode(serviceAccountBase64.trim());
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)))
                .build();
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        log.info("[Push] Firebase 프로젝트 {} 로 푸시 알림을 보냅니다.", app.getOptions().getProjectId());
        return PushGateway.of(FirebaseMessaging.getInstance(app));
    }
}
