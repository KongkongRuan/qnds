package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.dto.UnifiedTaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 统一运维任务状态存储
 */
@Service
public class UnifiedTaskStore {

    private static final Logger log = LoggerFactory.getLogger(UnifiedTaskStore.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final String redisKeyPrefix;
    private final long ttlSeconds;
    private final ConcurrentHashMap<String, UnifiedTaskRecord> localStore = new ConcurrentHashMap<>();

    public UnifiedTaskStore(RedisTemplate<String, Object> redisTemplate,
                            @Value("${unified.task.redis-prefix:qdns:unified:task:}") String redisKeyPrefix,
                            @Value("${unified.task.ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    public void save(UnifiedTaskRecord taskRecord) {
        localStore.put(taskRecord.getTaskId(), taskRecord);
        persistToRedis(taskRecord);
    }

    public UnifiedTaskRecord get(String taskId) {
        UnifiedTaskRecord fromRedis = loadFromRedis(taskId);
        if (fromRedis != null) {
            localStore.put(taskId, fromRedis);
            return fromRedis;
        }
        UnifiedTaskRecord taskRecord = localStore.get(taskId);
        if (taskRecord == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        return taskRecord;
    }

    public void markRunning(String taskId, String statusMessage) {
        update(taskId, taskRecord -> {
            taskRecord.setStatus("RUNNING");
            taskRecord.setStatusMessage(statusMessage);
            if (taskRecord.getStartedAt() == null) {
                taskRecord.setStartedAt(new Date());
            }
        });
    }

    public void markFinished(String taskId, boolean success, String statusMessage, Object downstream) {
        update(taskId, taskRecord -> {
            taskRecord.setStatus(success ? "SUCCESS" : "FAILED");
            taskRecord.setStatusMessage(statusMessage);
            taskRecord.setDownstream(downstream);
            if (taskRecord.getStartedAt() == null) {
                taskRecord.setStartedAt(new Date());
            }
            taskRecord.setFinishedAt(new Date());
        });
    }

    private void update(String taskId, TaskUpdater updater) {
        UnifiedTaskRecord taskRecord = get(taskId);
        synchronized (taskRecord) {
            updater.apply(taskRecord);
            localStore.put(taskId, taskRecord);
            persistToRedis(taskRecord);
        }
    }

    private void persistToRedis(UnifiedTaskRecord taskRecord) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(buildRedisKey(taskRecord.getTaskId()), taskRecord, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("统一任务写入Redis失败，回退内存存储: taskId={}, error={}", taskRecord.getTaskId(), e.getMessage());
        }
    }

    private UnifiedTaskRecord loadFromRedis(String taskId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(buildRedisKey(taskId));
            if (value instanceof UnifiedTaskRecord) {
                return (UnifiedTaskRecord) value;
            }
        } catch (Exception e) {
            log.debug("统一任务从Redis读取失败，回退内存存储: taskId={}, error={}", taskId, e.getMessage());
        }
        return null;
    }

    private String buildRedisKey(String taskId) {
        return redisKeyPrefix + taskId;
    }

    @FunctionalInterface
    private interface TaskUpdater {
        void apply(UnifiedTaskRecord taskRecord);
    }
}
