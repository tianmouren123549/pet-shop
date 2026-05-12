package com.gzu.petshop.config;

import com.gzu.petshop.service.recommend.RecommendationScriptRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按 Cron 定时触发离线推荐脚本（需配置脚本路径与开关）。
 * <p>生产环境更常见做法：用操作系统计划任务 / Airflow / CI 调度 Python，不必依赖本类。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.refresh-recommendations-scheduled-enabled", havingValue = "true")
public class DemoRecommendationScheduledRefresh {

    private static final Logger log = LoggerFactory.getLogger(DemoRecommendationScheduledRefresh.class);

    private final RecommendationScriptRefreshService recommendationScriptRefreshService;

    public DemoRecommendationScheduledRefresh(RecommendationScriptRefreshService recommendationScriptRefreshService) {
        this.recommendationScriptRefreshService = recommendationScriptRefreshService;
    }

    @Scheduled(cron = "${app.demo.refresh-recommendations-cron:0 0 3 * * *}")
    public void onSchedule() {
        log.info("Scheduled recommendation refresh tick");
        recommendationScriptRefreshService.submitAsyncIfConfigured();
    }
}
