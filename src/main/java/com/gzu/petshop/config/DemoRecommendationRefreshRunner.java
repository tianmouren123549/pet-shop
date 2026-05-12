package com.gzu.petshop.config;

import com.gzu.petshop.service.recommend.RecommendationScriptRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 可选：在应用启动后异步执行脚本，刷新离线推荐写入 MySQL，
 * 使首页推荐随重启略有变化（通过更换 simulate_orders 随机种子，不改训练好的模型）。
 */
@Component
@Order(2000)
public class DemoRecommendationRefreshRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRecommendationRefreshRunner.class);

    @Value("${app.demo.refresh-recommendations-on-startup:false}")
    private boolean refreshOnStartup;

    private final RecommendationScriptRefreshService recommendationScriptRefreshService;

    public DemoRecommendationRefreshRunner(RecommendationScriptRefreshService recommendationScriptRefreshService) {
        this.recommendationScriptRefreshService = recommendationScriptRefreshService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!refreshOnStartup) {
            log.info("Demo recommendation refresh on startup disabled (app.demo.refresh-recommendations-on-startup=false)");
            return;
        }
        recommendationScriptRefreshService.submitAsyncIfConfigured();
    }
}
