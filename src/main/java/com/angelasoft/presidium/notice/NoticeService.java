package com.angelasoft.presidium.notice;

import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import com.angelasoft.presidium.notice.NoticeDtos.NoticePage;
import com.angelasoft.presidium.notice.NoticeDtos.NoticeRequest;
import com.angelasoft.presidium.notice.NoticeDtos.NoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지사항. 단원은 조회만, 4간부는 등록·수정·삭제할 수 있다. */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TITLE = 100;
    private static final int MAX_CONTENT = 3000;

    private final MembershipLookup membershipLookup;
    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public NoticePage list(Long userId, int page, int size) {
        PresidiumMembership me = membershipLookup.require(userId);
        Slice<Notice> slice = noticeRepository.findByPresidiumIdOrderByCreatedAtDescIdDesc(
                me.getPresidium().getId(),
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE))));
        return new NoticePage(slice.getContent().stream().map(NoticeResponse::from).toList(), slice.hasNext());
    }

    @Transactional(readOnly = true)
    public NoticeResponse get(Long userId, Long noticeId) {
        return NoticeResponse.from(find(membershipLookup.require(userId), noticeId));
    }

    @Transactional
    public NoticeResponse create(Long userId, NoticeRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        validate(request);
        Notice saved = noticeRepository.save(Notice.builder()
                .presidium(me.getPresidium())
                .title(request.title().trim())
                .content(request.content().trim())
                .createdBy(me.getUser())
                .build());
        return NoticeResponse.from(saved);
    }

    @Transactional
    public NoticeResponse update(Long userId, Long noticeId, NoticeRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        validate(request);
        Notice notice = find(me, noticeId);
        notice.update(request.title().trim(), request.content().trim());
        return NoticeResponse.from(notice);
    }

    @Transactional
    public void delete(Long userId, Long noticeId) {
        PresidiumMembership me = requireOfficer(userId);
        noticeRepository.delete(find(me, noticeId));
    }

    private PresidiumMembership requireOfficer(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("공지사항은 4간부만 등록·수정·삭제할 수 있습니다.");
        }
        return me;
    }

    private Notice find(PresidiumMembership me, Long noticeId) {
        return noticeRepository.findByIdAndPresidiumId(noticeId, me.getPresidium().getId())
                .orElseThrow(() -> GovernanceException.notFound("공지사항을 찾을 수 없습니다."));
    }

    private static void validate(NoticeRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw GovernanceException.badRequest("제목을 입력해 주세요.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw GovernanceException.badRequest("내용을 입력해 주세요.");
        }
        if (request.title().trim().length() > MAX_TITLE) {
            throw GovernanceException.badRequest("제목은 " + MAX_TITLE + "자 이내로 입력해 주세요.");
        }
        if (request.content().trim().length() > MAX_CONTENT) {
            throw GovernanceException.badRequest("내용은 " + MAX_CONTENT + "자 이내로 입력해 주세요.");
        }
    }
}
