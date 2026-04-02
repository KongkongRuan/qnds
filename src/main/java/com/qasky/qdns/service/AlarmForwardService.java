package com.qasky.qdns.service;

import com.qasky.qdns.model.AlarmInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.ArrayList;

/**
 * 告警转发服务 - 转发到 qdms 平台
 */
@Service
public class AlarmForwardService {

    private static final Logger log = LoggerFactory.getLogger(AlarmForwardService.class);

    @Value("${alarm.enabled:true}")
    private boolean enabled;

    @Value("${alarm.platform-url:}")
    private String platformUrl;

    @Value("${alarm.retry-count:3}")
    private int retryCount;

    @Value("${alarm.retry-interval-ms:1000}")
    private int retryIntervalMs;

    private RestTemplate restTemplate;
    private ExecutorService executorService;
    
    // 缓存最近收到的告警，供前端展示
    private final ConcurrentLinkedQueue<AlarmInfo> recentAlarms = new ConcurrentLinkedQueue<>();
    private static final int MAX_ALARM_CACHE_SIZE = 100;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        this.executorService = Executors.newFixedThreadPool(4);

        if (enabled && (platformUrl == null || platformUrl.isEmpty())) {
            log.warn("告警转发已启用但未配置platform-url，告警将仅打印日志");
        }
    }

    /**
     * 转发告警到 qdms 平台
     */
    public void forwardAlarm(AlarmInfo alarm) {
        // 加入缓存
        recentAlarms.offer(alarm);
        while (recentAlarms.size() > MAX_ALARM_CACHE_SIZE) {
            recentAlarms.poll();
        }

        if (!enabled) {
            log.debug("告警转发已禁用: {}", alarm);
            return;
        }

        executorService.submit(() -> doForwardAlarm(alarm));
    }
    
    /**
     * 获取最近收到的告警
     */
    public List<AlarmInfo> getRecentAlarms() {
        return new ArrayList<>(recentAlarms);
    }

    /**
     * 执行告警转发到 qdms
     */
    private void doForwardAlarm(AlarmInfo alarm) {
        if (platformUrl == null || platformUrl.isEmpty()) {
            log.info("告警信息(未转发): 设备={}, 告警码={}, 数据={}",
                    alarm.getAlarmSource(), alarm.getAlarmCode(), alarm.getAlarmData());
            return;
        }

        for (int i = 0; i < retryCount; i++) {
            try {
                // 构建 qdms 所需的 AlarmVO 格式
                Map<String, Object> alarmVO = buildQdmsAlarmVO(alarm);

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(alarmVO, headers);

                log.info("转发告警到 qdms: {} -> {}", alarm.getAlarmSource(), platformUrl);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        platformUrl, request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("告警转发成功: 设备={}, 告警码={}",
                            alarm.getAlarmSource(), alarm.getAlarmCode());
                    return;
                } else {
                    log.warn("告警转发返回非2xx状态: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("告警转发失败(第{}次): {}", i + 1, e.getMessage());

                if (i < retryCount - 1) {
                    try {
                        Thread.sleep(retryIntervalMs * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("告警转发最终失败: 设备={}, 告警码={}",
                alarm.getAlarmSource(), alarm.getAlarmCode());
    }

    /**
     * 构建 qdms 所需的 AlarmVO 对象
     */
    private Map<String, Object> buildQdmsAlarmVO(AlarmInfo alarm) {
        Map<String, Object> alarmVO = new HashMap<>();

        // 告警源（设备ID）
        alarmVO.put("alarmSource", alarm.getAlarmSource());

        // 告警类型（使用 alarmCode 作为类型）
        alarmVO.put("alarmType", alarm.getAlarmCode());

        // 告警数据
        alarmVO.put("alarmData", alarm.getAlarmData());

        // 告警级别（转换为字符串）
        alarmVO.put("alarmLevel", String.valueOf(alarm.getAlarmLevel()));

        // 告警状态：0-未处理
        alarmVO.put("alarmStatus", 0);

        // 告警次数，默认为1
        alarmVO.put("alarmCount", 1);

        // 发生时间
        alarmVO.put("occurTime", alarm.getAlarmCreateDate());

        return alarmVO;
    }

    /**
     * 同步转发告警（阻塞等待结果）
     */
    public boolean forwardAlarmSync(AlarmInfo alarm) {
        if (!enabled || platformUrl == null || platformUrl.isEmpty()) {
            log.info("告警信息(未转发): {}", alarm);
            return false;
        }

        try {
            // 构建 qdms 所需的 AlarmVO 格式
            Map<String, Object> alarmVO = buildQdmsAlarmVO(alarm);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(alarmVO, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    platformUrl, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("告警同步转发失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPlatformUrl() {
        return platformUrl;
    }
}
