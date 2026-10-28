package com.angelasoft.activity.report;

import com.angelasoft.activity.Activity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주차 도입 전 활동보고에 week_start 를 채운다. 그때는 제출할 때마다 따로 쌓였으므로
 * 같은 단원·쁘레시디움·주의 보고는 활동별 횟수를 더해 가장 먼저 제출한 한 건으로 합친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityReportWeekBackfill implements ApplicationRunner {

    private final ActivityReportRepository reportRepository;
    private final ActivityReportItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ActivityReport> pending = reportRepository.findWithoutWeekStart();
        if (pending.isEmpty()) {
            return;
        }

        Map<String, List<ActivityReport>> groups = new LinkedHashMap<>();
        for (ActivityReport report : pending) {
            LocalDate weekStart = ActivityReportService.weekStartOf(report.getReportDate());
            String key = report.getPresidium().getId() + ":" + report.getMember().getId() + ":" + weekStart;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(report);
        }

        int merged = 0;
        for (List<ActivityReport> group : groups.values()) {
            ActivityReport first = group.get(0);
            LocalDate weekStart = ActivityReportService.weekStartOf(first.getReportDate());
            ActivityReport keeper = reportRepository
                    .findWeekly(first.getPresidium().getId(), first.getMember().getId(), weekStart)
                    .orElse(first);

            List<ActivityReport> all = new ArrayList<>(group);
            if (!all.contains(keeper)) {
                all.add(0, keeper);
            }
            if (all.size() == 1) {
                keeper.assignWeekStart(weekStart);
                continue;
            }

            Map<Long, Activity> activities = new LinkedHashMap<>();
            Map<Long, Integer> counts = new LinkedHashMap<>();
            for (ActivityReport report : all) {
                List<ActivityReportItem> reportItems = itemRepository.findByReportId(report.getId());
                for (ActivityReportItem item : reportItems) {
                    activities.putIfAbsent(item.getActivity().getId(), item.getActivity());
                    counts.merge(item.getActivity().getId(), item.getQuantity(), Integer::sum);
                }
                itemRepository.deleteAll(reportItems);
            }
            for (ActivityReport report : all) {
                if (report != keeper) {
                    reportRepository.delete(report);
                    merged++;
                }
            }
            reportRepository.flush();

            keeper.assignWeekStart(weekStart);
            List<ActivityReportItem> items = new ArrayList<>();
            counts.forEach((activityId, count) -> items.add(ActivityReportItem.builder()
                    .report(keeper)
                    .activity(activities.get(activityId))
                    .quantity(count)
                    .build()));
            itemRepository.saveAll(items);
        }

        log.info("[ActivityReportWeekBackfill] 주차 채움 {}건, 같은 주 보고 합침 {}건", pending.size(), merged);
    }
}
