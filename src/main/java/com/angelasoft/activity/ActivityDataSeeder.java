package com.angelasoft.activity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 시작 시 data/activities.csv 와 DB 활동 목록을 맞춘다.
 *
 * - CSV 에 있고 DB 에 없으면 추가, 있으면 활성화하고 CSV 순서로 정렬값 갱신
 * - DB 에 있지만 CSV 에서 빠졌으면 비활성화 (보고 기록 보존을 위해 삭제하지 않음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityDataSeeder implements ApplicationRunner {

    private static final String CSV_PATH = "data/activities.csv";

    private final ActivityRepository activityRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<String> names = readNames();
        if (names.isEmpty()) {
            log.warn("[ActivitySeeder] CSV가 비어 있어 동기화하지 않음: {}", CSV_PATH);
            return;
        }

        Map<String, Activity> existing = activityRepository.findAll().stream()
                .collect(Collectors.toMap(Activity::getName, Function.identity()));

        int added = 0;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Activity activity = existing.remove(name);
            if (activity == null) {
                activityRepository.save(Activity.builder().name(name).sortOrder(i + 1).build());
                added++;
            } else {
                activity.activate(i + 1);
            }
        }

        int deactivated = 0;
        for (Activity stale : existing.values()) {
            if (stale.isActive()) {
                stale.deactivate();
                deactivated++;
            }
        }

        log.info("[ActivitySeeder] 활동 동기화 완료 - 전체 {}, 추가 {}, 비활성화 {}",
                names.size(), added, deactivated);
    }

    private List<String> readNames() throws IOException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String name = line.strip();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }
}
