package com.angelasoft.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 매일 도는 작업 (간부 임기 만료 알림 등). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
