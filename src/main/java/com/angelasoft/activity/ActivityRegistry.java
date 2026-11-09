package com.angelasoft.activity;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 단원이 요청해 4간부가 승인한 활동을 모든 쁘레시디움이 쓰는 활동 목록에 더한다. */
@Service
@RequiredArgsConstructor
public class ActivityRegistry {

    public static final int MAX_NAME_LENGTH = 30;

    private final ActivityRepository activityRepository;

    /** 공백을 빼고 같은 이름의 활동이 목록에 이미 있으면 그 활동. */
    @Transactional(readOnly = true)
    public Optional<Activity> findActive(String name) {
        return find(name).filter(Activity::isActive);
    }

    /** 목록에 더한다. 같은 이름이 이미 있으면 그대로 두고, 빠져 있던 활동이면 다시 목록에 넣는다. */
    @Transactional
    public Activity addCustom(String name) {
        String trimmed = name.strip();
        int nextOrder = activityRepository.maxSortOrder() + 1;
        return find(trimmed)
                .map(existing -> {
                    if (!existing.isActive()) {
                        existing.activateAsCustom(nextOrder);
                    }
                    return existing;
                })
                .orElseGet(() -> activityRepository.save(
                        Activity.builder().name(trimmed).sortOrder(nextOrder).custom(true).build()));
    }

    private Optional<Activity> find(String name) {
        String key = Activity.normalize(name);
        return activityRepository.findAll().stream()
                .filter(a -> Activity.normalize(a.getName()).equals(key))
                .findFirst();
    }
}
