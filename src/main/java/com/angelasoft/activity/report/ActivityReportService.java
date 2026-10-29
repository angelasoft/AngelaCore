package com.angelasoft.activity.report;

import com.angelasoft.activity.Activity;
import com.angelasoft.activity.ActivityRepository;
import com.angelasoft.activity.report.ActivityReportDtos.ActivityCount;
import com.angelasoft.activity.report.ActivityReportDtos.MemberCount;
import com.angelasoft.activity.report.ActivityReportDtos.MonthCount;
import com.angelasoft.activity.report.ActivityReportDtos.MonthlyStats;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitItem;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitResponse;
import com.angelasoft.activity.report.ActivityReportDtos.WeeklyItem;
import com.angelasoft.activity.report.ActivityReportDtos.WeeklyReport;
import com.angelasoft.activity.report.ActivityReportDtos.YearlyTrend;
import com.angelasoft.activity.report.ActivityReportProjections.DateTotal;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동보고 제출과 쁘레시디움 활동 통계. 보고는 단원마다 주(월~일) 단위로 한 건이며,
 * 통계는 그 주에 처음 제출한 날짜(한국 날짜) 기준이고 모든 단원이 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ActivityReportService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_COUNT_PER_ITEM = 999;

    private final MembershipLookup membershipLookup;
    private final ActivityRepository activityRepository;
    private final ActivityReportRepository reportRepository;
    private final ActivityReportItemRepository itemRepository;

    /** 날짜가 속한 주의 월요일. 한 주는 월요일~일요일이다. */
    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 이번 주 내 보고. 앱은 이 값으로 입력칸을 채우고, 주가 바뀌면 빈 보고가 내려간다. */
    @Transactional(readOnly = true)
    public WeeklyReport currentWeek(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        LocalDate weekStart = weekStartOf(LocalDate.now(KOREA));
        return reportRepository.findWeekly(me.getPresidium().getId(), me.getUser().getId(), weekStart)
                .map(report -> {
                    List<WeeklyItem> items = itemRepository.findByReportId(report.getId()).stream()
                            .map(i -> new WeeklyItem(i.getActivity().getId(), i.getQuantity()))
                            .toList();
                    int total = items.stream().mapToInt(WeeklyItem::count).sum();
                    return new WeeklyReport(weekStart, weekStart.plusDays(6), report.getUpdatedAt(), total, items);
                })
                .orElseGet(() -> new WeeklyReport(weekStart, weekStart.plusDays(6), null, 0, List.of()));
    }

    /**
     * 이번 주 보고를 보낸 값으로 통째로 바꾼다. 앱은 이번 주 누적 횟수 전체를 보내므로
     * 몇 번을 다시 제출해도 통계에는 마지막 값만 한 번 반영된다.
     * 이미 보고한 주에 모든 횟수를 0 으로 보내면 그 주 보고를 지운다.
     */
    @Transactional
    public SubmitResponse submit(Long userId, List<SubmitItem> items) {
        PresidiumMembership me = membershipLookup.require(userId);

        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (SubmitItem item : items == null ? List.<SubmitItem>of() : items) {
            if (item == null || item.activityId() == null || item.count() == null || item.count() <= 0) {
                continue;
            }
            if (item.count() > MAX_COUNT_PER_ITEM) {
                throw GovernanceException.badRequest("한 활동의 횟수는 " + MAX_COUNT_PER_ITEM + "회까지 입력할 수 있습니다.");
            }
            counts.merge(item.activityId(), item.count(), Integer::sum);
        }

        LocalDate today = LocalDate.now(KOREA);
        LocalDate weekStart = weekStartOf(today);
        ActivityReport existing = reportRepository
                .findWeekly(me.getPresidium().getId(), me.getUser().getId(), weekStart)
                .orElse(null);

        if (counts.isEmpty()) {
            if (existing == null) {
                throw GovernanceException.badRequest("보고할 활동 횟수를 1개 이상 입력해 주세요.");
            }
            itemRepository.deleteByReportId(existing.getId());
            reportRepository.delete(existing);
            return new SubmitResponse(null, null, weekStart, 0);
        }

        Map<Long, Activity> activities = new LinkedHashMap<>();
        for (Activity activity : activityRepository.findAllById(counts.keySet())) {
            if (activity.isActive()) {
                activities.put(activity.getId(), activity);
            }
        }
        if (activities.size() != counts.size()) {
            throw GovernanceException.badRequest("목록에 없는 활동이 있습니다. 활동 목록을 새로 고친 뒤 다시 제출해 주세요.");
        }

        ActivityReport report;
        if (existing != null) {
            report = existing;
            report.markUpdated();
            itemRepository.deleteByReportId(report.getId());
        } else {
            try {
                report = reportRepository.saveAndFlush(ActivityReport.builder()
                        .presidium(me.getPresidium())
                        .member(me.getUser())
                        .reportDate(today)
                        .weekStart(weekStart)
                        .build());
            } catch (DataIntegrityViolationException e) {
                throw GovernanceException.conflict("이번 주 보고가 동시에 제출되었습니다. 잠시 후 다시 제출해 주세요.");
            }
        }

        List<ActivityReportItem> reportItems = new ArrayList<>();
        counts.forEach((activityId, count) -> reportItems.add(ActivityReportItem.builder()
                .report(report)
                .activity(activities.get(activityId))
                .quantity(count)
                .build()));
        itemRepository.saveAll(reportItems);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new SubmitResponse(report.getId(), report.getReportDate(), weekStart, total);
    }

    /**
     * 한 달 또는 한 주(week 가 있으면 그 날짜가 속한 월~일)의 통계.
     * 보고 날짜는 늘 그 보고의 주 안에 있으므로 주 통계는 보고 날짜 범위로 구한다.
     */
    @Transactional(readOnly = true)
    public MonthlyStats monthly(Long userId, String month, String week, Long memberId) {
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        LocalDate weekStart = week == null || week.isBlank() ? null : weekStartOf(parseDate(week));
        YearMonth ym = weekStart != null ? YearMonth.from(weekStart) : parseMonth(month);
        LocalDate from = weekStart != null ? weekStart : ym.atDay(1);
        LocalDate to = weekStart != null ? weekStart.plusDays(6) : ym.atEndOfMonth();

        List<ActivityCount> activities = (memberId == null
                ? itemRepository.sumByActivity(presidiumId, from, to)
                : itemRepository.sumByActivityForMember(presidiumId, memberId, from, to))
                .stream()
                .map(t -> new ActivityCount(t.getActivityId(), t.getName(), t.getTotal()))
                .toList();
        List<MemberCount> members = memberId != null
                ? List.of()
                : itemRepository.sumByMember(presidiumId, from, to).stream()
                        .map(t -> new MemberCount(t.getUserId(), displayName(t.getName(), t.getBaptismalName()), t.getTotal()))
                        .toList();
        long total = activities.stream().mapToLong(ActivityCount::count).sum();
        return new MonthlyStats(ym.toString(), weekStart, weekStart == null ? null : to,
                memberId, total, activities, members);
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw GovernanceException.badRequest("week 는 2026-10-26 형식으로 보내 주세요.");
        }
    }

    @Transactional(readOnly = true)
    public YearlyTrend yearly(Long userId, Integer year, Long memberId) {
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        int y = year == null ? LocalDate.now(KOREA).getYear() : year;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        List<DateTotal> byDate = memberId == null
                ? itemRepository.sumByDate(presidiumId, from, to)
                : itemRepository.sumByDateForMember(presidiumId, memberId, from, to);
        long[] perMonth = new long[12];
        for (DateTotal t : byDate) {
            perMonth[t.getReportDate().getMonthValue() - 1] += t.getTotal();
        }
        List<MonthCount> months = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new MonthCount(m, perMonth[m - 1]))
                .toList();
        return new YearlyTrend(y, memberId, months);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(KOREA);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw GovernanceException.badRequest("month 는 2026-10 형식으로 보내 주세요.");
        }
    }

    private static String displayName(String name, String baptismalName) {
        return baptismalName == null || baptismalName.isBlank() ? name : name + "(" + baptismalName + ")";
    }
}
