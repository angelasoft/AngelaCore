package com.angelasoft.notification.push;

import com.google.firebase.messaging.FirebaseMessaging;

/** FirebaseMessaging 을 감싼다. 키가 없으면 messaging 이 null 이고 푸시를 보내지 않는다. */
public record PushGateway(FirebaseMessaging messaging) {

    static PushGateway disabled() {
        return new PushGateway(null);
    }

    static PushGateway of(FirebaseMessaging messaging) {
        return new PushGateway(messaging);
    }

    public boolean enabled() {
        return messaging != null;
    }
}
