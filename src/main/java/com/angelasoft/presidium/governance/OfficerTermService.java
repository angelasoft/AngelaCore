package com.angelasoft.presidium.governance;

import static com.angelasoft.presidium.governance.GovernanceDtos.displayName;

import com.angelasoft.notification.NotificationService;
import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.MemberResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.OfficerTermRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간부 임기. 임명일·임기를 입력하고(초기 설정을 위해 지난 날짜도 가능), 임기 마지막 날 한 달 전이 되면
 * 단원 전체에게 간부 이동이 필요하다고 알린다 (임기마다 한 번).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficerTermService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    static final int DEFAULT_TERM_MONTHS = 36;
    private static final int MAX_TERM_MONTHS = 120;
    private static final LocalDate EARLIEST = LocalDate.of(1921, 9, 7);

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final NotificationService notificationService;

    /** 4간부가 지금 간부의 임명일·임기를 입력하거나 고친다. */
    @Transactional
    public MemberResponse updateTerm(Long userId, Long officerUserId, OfficerTermRequest request) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("간부 임기는 4간부만 입력할 수 있습니다.");
        }
        PresidiumMembership officer = membershipRepository
                .findByPresidiumIdAndUserId(me.getPresidium().getId(), officerUserId)
                .filter(PresidiumMembership::isOfficer)
                .orElseThrow(() -> GovernanceException.notFound("이 쁘레시디움의 간부가 아닙니다."));

        officer.updateOfficerTerm(parseAppointedDate(request.appointedDate()), termMonths(request));
        notifyIfEnding(officer, today());
        return MemberResponse.of(officer, userId);
    }

    /** 매일 아침 9시: 임기 마지막 날까지 한 달 남은 간부가 있으면 알린다. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyEndingTerms() {
        LocalDate today = today();
        int sent = 0;
        for (PresidiumMembership officer : membershipRepository.findOfficersAwaitingTermNotice()) {
            if (notifyIfEnding(officer, today)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("간부 임기 만료 알림 {}건 전송", sent);
        }
    }

    /** 임기 마지막 날 한 달 전부터, 이번 임기에 아직 안 보냈으면 단원 전체에게 알린다. */
    boolean notifyIfEnding(PresidiumMembership officer, LocalDate today) {
        LocalDate end = officer.getOfficerTermEndDate();
        if (end == null || officer.isOfficerTermNoticeSent() || today.isBefore(end.minusMonths(1))) {
            return false;
        }
        String who = "%s %s 단원".formatted(officer.getOfficerRole().getLabel(), displayName(officer.getUser()));
        String date = "%d년 %d월 %d일".formatted(end.getYear(), end.getMonthValue(), end.getDayOfMonth());
        String message = today.isAfter(end)
                ? "%s의 임기가 %s에 끝났습니다. 간부 이동이 필요합니다.".formatted(who, date)
                : "%s의 임기가 %s에 끝납니다. 간부 이동이 필요하니 간부 이동 투표를 준비해 주세요.".formatted(who, date);

        List<Long> memberIds = membershipRepository.findByPresidiumIdWithUser(officer.getPresidium().getId()).stream()
                .map(m -> m.getUser().getId())
                .toList();
        notificationService.notify(memberIds, NotificationType.OFFICER_TERM_ENDING,
                NotificationService.presidiumTag(officer.getPresidium().getName()) + "간부 이동 필요",
                message, officer.getPresidium().getId());
        officer.markOfficerTermNoticeSent();
        return true;
    }

    static LocalDate today() {
        return LocalDate.now(KOREA);
    }

    static LocalDate parseAppointedDate(String value) {
        if (value == null || value.isBlank()) {
            throw GovernanceException.badRequest("임명일을 골라 주세요.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw GovernanceException.badRequest("임명일은 2026-01-07 형식으로 보내 주세요.");
        }
        if (date.isAfter(today())) {
            throw GovernanceException.badRequest("임명일은 오늘이나 지난 날짜만 고를 수 있습니다.");
        }
        if (date.isBefore(EARLIEST)) {
            throw GovernanceException.badRequest("임명일을 다시 확인해 주세요.");
        }
        return date;
    }

    /** 년·개월을 개월로. 둘 다 없으면 3년 0개월. */
    static int termMonths(OfficerTermRequest request) {
        if (request.termYears() == null && request.termMonths() == null) {
            return DEFAULT_TERM_MONTHS;
        }
        int years = request.termYears() == null ? 0 : request.termYears();
        int months = request.termMonths() == null ? 0 : request.termMonths();
        if (years < 0 || months < 0 || months > 11) {
            throw GovernanceException.badRequest("임기는 0~11개월 단위로 입력해 주세요.");
        }
        int total = years * 12 + months;
        if (total < 1 || total > MAX_TERM_MONTHS) {
            throw GovernanceException.badRequest("임기는 1개월에서 10년 사이로 입력해 주세요.");
        }
        return total;
    }
}
