package com.angelasoft.presidium.notice;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.notice.NoticeDtos.NoticePage;
import com.angelasoft.presidium.notice.NoticeDtos.NoticeRequest;
import com.angelasoft.presidium.notice.NoticeDtos.NoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 쁘레시디움 공지사항 API (인증 필요).
 *
 *   GET    /app/presidiums/me/notices?page=0&size=20   최신순 (홈 카드는 size=3)
 *   GET    /app/presidiums/me/notices/{id}
 *   POST   /app/presidiums/me/notices                  {title, content}  4간부만
 *   PUT    /app/presidiums/me/notices/{id}             {title, content}  4간부만
 *   DELETE /app/presidiums/me/notices/{id}             4간부만
 */
@RestController
@RequestMapping("/app/presidiums/me/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public NoticePage list(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return noticeService.list(principal.getUserId(), page, size);
    }

    @GetMapping("/{noticeId}")
    public NoticeResponse get(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable Long noticeId) {
        return noticeService.get(principal.getUserId(), noticeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse create(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody NoticeRequest request
    ) {
        return noticeService.create(principal.getUserId(), request);
    }

    @PutMapping("/{noticeId}")
    public NoticeResponse update(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long noticeId,
            @RequestBody NoticeRequest request
    ) {
        return noticeService.update(principal.getUserId(), noticeId, request);
    }

    @DeleteMapping("/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable Long noticeId) {
        noticeService.delete(principal.getUserId(), noticeId);
    }
}
