package com.angelasoft.presidium.notice;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    public record NoticeRequest(String title, String content) {
    }

    /** 시각은 앱에서 현지 시간으로 바꿀 수 있게 UTC(Instant)로 내려준다. */
    public record NoticeResponse(
            Long id,
            String title,
            String content,
            String createdByName,
            Instant createdAt,
            Instant updatedAt
    ) {
        static NoticeResponse from(Notice n) {
            String name = n.getCreatedBy().getName();
            String baptismal = n.getCreatedBy().getBaptismalName();
            String createdBy = baptismal == null || baptismal.isBlank() ? name : name + "(" + baptismal + ")";
            ZoneId zone = ZoneId.systemDefault();
            return new NoticeResponse(
                    n.getId(), n.getTitle(), n.getContent(), createdBy,
                    n.getCreatedAt().atZone(zone).toInstant(),
                    n.getUpdatedAt().atZone(zone).toInstant());
        }
    }

    public record NoticePage(List<NoticeResponse> items, boolean hasNext) {
    }
}
