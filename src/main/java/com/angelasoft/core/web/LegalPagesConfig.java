package com.angelasoft.core.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 스토어·AdMob 에 등록하는 공개 문서 주소. 내용은 static/ 의 HTML 이다. */
@Configuration
public class LegalPagesConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/privacy").setViewName("forward:/privacy.html");
    }
}
