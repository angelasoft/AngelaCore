package com.angelasoft.notification.push;

import java.util.Collection;
import java.util.Map;

/** 앱 알림함에 알림이 저장되면 발행된다. 트랜잭션이 커밋된 뒤에 푸시로 보낸다. */
public record PushRequested(Collection<Long> userIds, String title, String body, Map<String, String> data) {
}
