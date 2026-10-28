package com.angelasoft.activity.report;

import com.angelasoft.activity.Activity;
import com.angelasoft.activity.ActivityRepository;
import com.angelasoft.activity.report.ActivityReportDtos.ActivityCount;
import com.angelasoft.activity.report.ActivityReportDtos.MemberCount;
import com.angelasoft.activity.report.ActivityReportDtos.MonthCount;
import com.angelasoft.activity.report.ActivityReportDtos.MonthlyStats;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitItem;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitResponse;
import com.angelasoft.activity.report.ActivityReportDtos.YearlyTrend;
import com.angelasoft.activity.report.ActivityReportProjections.DateTotal;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활동보고 제출과 쁘레시디움 활동 통계. 통계는 제출한 날짜(한국 날짜) 기준이며 모든 단원이 볼 수 있다. */
@Service
@RequiredArgsConstructor
public class ActivityReportService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_COUNT_PER_ITEM = 999;

    private final MembershipLookup membershipLookup;
    private final ActivityRepository activityRepository;
    private final ActivityReportRepository reportRepository;
    private final ActivityReportItemRepository itemRepository;

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
        if (counts.isEmpty()) {
            throw GovernanceException.badRequest("보고할 활동 횟수를 1개 이상 입력해 주세요.");
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

        ActivityReport report = reportRepository.save(ActivityReport.builder()
                .presidium(me.getPresidium())
                .member(me.getUser())
                .reportDate(LocalDate.now(KOREA))
                .build());
        List<ActivityReportItem> reportItems = new ArrayList<>();
        counts.forEach((activityId, count) -> reportItems.add(ActivityReportItem.builder()
                .report(report)
                .activity(activities.get(activityId))
                .quantity(count)
                .build()));
        itemRepository.saveAll(reportItems);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new SubmitResponse(report.getId(), report.getReportDate(), total);
    }

    @Transactional(readOnly = true)
    public MonthlyStats monthly(Long userId, String month, Long memberId) {
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        YearMonth ym = parseMonth(month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

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
        return new MonthlyStats(ym.toString(), memberId, total, activities, members);
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
