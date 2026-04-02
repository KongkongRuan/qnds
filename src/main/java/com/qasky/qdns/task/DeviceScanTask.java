package com.qasky.qdns.task;

import com.qasky.qdns.config.CollectorConfig;
import com.qasky.qdns.service.DeviceCollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描设备状态任务。
 * <p>
 * 开启 {@code collector.split-schedule-enabled} 时：按间隔做高频指标采集，按 Cron 做全量采集；
 * 关闭时：仅按间隔全量采集（与旧版行为一致）。
 */
@Component
public class DeviceScanTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceScanTask.class);

    private final DeviceCollectorService collectorService;
    private final CollectorConfig collectorConfig;

    public DeviceScanTask(DeviceCollectorService collectorService, CollectorConfig collectorConfig) {
        this.collectorService = collectorService;
        this.collectorConfig = collectorConfig;
    }

    /**
     * 拆分模式：高频指标；非拆分模式：全量采集
     */
    @Scheduled(
            initialDelayString = "${collector.initial-delay:10}000",
            fixedDelayString = "${collector.scan-interval:60}000"
    )
    public void scheduledIntervalCollect() {
        try {
            if (collectorConfig.isSplitScheduleEnabled()) {
                log.debug("定时高频采集触发 (间隔 {}s)", collectorConfig.getScanInterval());
                collectorService.collectMetricsRound();
            } else {
                log.debug("定时全量采集触发 (间隔 {}s)", collectorConfig.getScanInterval());
                collectorService.collectAll();
            }
        } catch (Exception e) {
            log.error("定时采集任务异常", e);
        }
    }

    /**
     * 仅拆分模式：按计划执行全量采集（接口表、VPN 表等）
     */
    @Scheduled(cron = "${collector.full-scan-cron:0 0 2 * * ?}")
    public void scheduledFullCollect() {
        if (!collectorConfig.isSplitScheduleEnabled()) {
            return;
        }
        log.info("定时全量采集触发 (cron={})", collectorConfig.getFullScanCron());
        try {
            collectorService.collectAll();
        } catch (Exception e) {
            log.error("定时全量采集异常", e);
        }
    }
}
