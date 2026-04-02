package com.qasky.qdns.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "collector")
public class CollectorConfig {

    private int scanInterval = 60;
    private int initialDelay = 10;
    private int threadPoolSize = 10;
    private int batchSize = 50;

    /**
     * 为 true：按 scan-interval 做高频指标采集，按 full-scan-cron 做全量采集；
     * 为 false：仅按 scan-interval 执行全量采集（与旧行为一致）。
     */
    private boolean splitScheduleEnabled = true;

    /**
     * 全量采集 Cron，默认每天 02:00。
     */
    private String fullScanCron = "0 0 2 * * ?";
}
