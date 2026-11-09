package com.angelasoft.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByActiveTrueOrderBySortOrderAsc();

    @Query("SELECT COALESCE(MAX(a.sortOrder), 0) FROM Activity a")
    int maxSortOrder();
}
