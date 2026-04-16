package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import com.qasky.qdns.model.dto.UnifiedCommandResponseData;
import com.qasky.qdns.model.dto.UnifiedTaskRecord;
import com.qasky.qdns.service.DeviceCollectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一运维接口服务
 */
@Service
public class UnifiedCommandService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedCommandService.class);

    private final DeviceCollectorService deviceCollectorService;
    private final OperationCodeRegistry operationCodeRegistry;
    private final UnifiedTaskStore unifiedTaskStore;
    private final Map<String, UnifiedCommandExecutor> executorMap;
    private final Executor asyncExecutor;

    @Autowired
    public UnifiedCommandService(DeviceCollectorService deviceCollectorService,
                                 OperationCodeRegistry operationCodeRegistry,
                                 UnifiedTaskStore unifiedTaskStore,
                                 List<UnifiedCommandExecutor> executors,
                                 @Value("${unified.async.thread-pool-size:4}") int asyncThreadPoolSize) {
        this(deviceCollectorService, operationCodeRegistry, unifiedTaskStore, executors,
                buildExecutor(asyncThreadPoolSize));
    }

    UnifiedCommandService(DeviceCollectorService deviceCollectorService,
                          OperationCodeRegistry operationCodeRegistry,
                          UnifiedTaskStore unifiedTaskStore,
                          List<UnifiedCommandExecutor> executors,
                          Executor asyncExecutor) {
        this.deviceCollectorService = deviceCollectorService;
        this.operationCodeRegistry = operationCodeRegistry;
        this.unifiedTaskStore = unifiedTaskStore;
        this.asyncExecutor = asyncExecutor;
        this.executorMap = new LinkedHashMap<>();
        for (UnifiedCommandExecutor executor : executors) {
            this.executorMap.put(executor.executorKey(), executor);
        }
    }

    public UnifiedCommandResponseData executeCommand(UnifiedCommandRequest request) {
        UnifiedCommandRequest normalizedRequest = normalizeRequest(request);
        OperationCodeRegistry.OperationDefinition definition = operationCodeRegistry.getDefinition(
                normalizedRequest.getCode(), normalizedRequest.getOperation());
        operationCodeRegistry.validatePayload(definition, normalizedRequest.getPayload());

        DeviceInfo device = deviceCollectorService.getDeviceById(normalizedRequest.getDeviceId());
        if (device == null) {
            throw new NoSuchElementException("设备不存在: " + normalizedRequest.getDeviceId());
        }

        UnifiedCommandExecutor executor = executorMap.get(definition.getExecutorKey());
        if (executor == null) {
            throw new IllegalStateException("未找到执行器: " + definition.getExecutorKey());
        }

        log.info("统一接口执行: requestId={}, deviceId={}, code={}, operation={}, mode={}, payloadKeys={}",
                normalizedRequest.getRequestId(), normalizedRequest.getDeviceId(), normalizedRequest.getCode(),
                normalizedRequest.getOperation(), definition.getMode(), normalizedRequest.getPayload().keySet());

        if ("ASYNC".equals(definition.getMode())) {
            return submitAsync(normalizedRequest, definition, device, executor);
        }

        UnifiedCommandExecutor.ExecutionResult result = executor.execute(device, normalizedRequest, definition);
        return buildSyncResponse(normalizedRequest, definition, result);
    }

    public UnifiedTaskRecord getTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        return unifiedTaskStore.get(taskId.trim());
    }

    private UnifiedCommandRequest normalizeRequest(UnifiedCommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        UnifiedCommandRequest normalized = new UnifiedCommandRequest();
        normalized.setRequestId(firstNonBlank(request.getRequestId(), UUID.randomUUID().toString().replace("-", "")));
        normalized.setDeviceId(trimToNull(request.getDeviceId()));
        normalized.setCode(request.getCode());
        normalized.setOperation(trimToNull(request.getOperation()));
        normalized.setOperator(trimToNull(request.getOperator()));
        normalized.setPayload(copyPayload(request.getPayload()));

        if (normalized.getDeviceId() == null) {
            throw new IllegalArgumentException("deviceId不能为空");
        }
        if (normalized.getCode() == null) {
            throw new IllegalArgumentException("code不能为空");
        }
        if (normalized.getOperation() == null) {
            throw new IllegalArgumentException("operation不能为空");
        }
        normalized.setOperation(normalized.getOperation().toLowerCase(Locale.ROOT));
        return normalized;
    }

    private UnifiedCommandResponseData buildSyncResponse(UnifiedCommandRequest request,
                                                         OperationCodeRegistry.OperationDefinition definition,
                                                         UnifiedCommandExecutor.ExecutionResult result) {
        UnifiedCommandResponseData responseData = new UnifiedCommandResponseData();
        responseData.setRequestId(request.getRequestId());
        responseData.setDeviceId(request.getDeviceId());
        responseData.setCode(request.getCode());
        responseData.setOperation(definition.getOperation());
        responseData.setMode(definition.getMode());
        responseData.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        responseData.setStatusMessage(result.getStatusMessage());
        responseData.setDownstream(result.getDownstream());
        return responseData;
    }

    private UnifiedCommandResponseData submitAsync(UnifiedCommandRequest request,
                                                   OperationCodeRegistry.OperationDefinition definition,
                                                   DeviceInfo device,
                                                   UnifiedCommandExecutor executor) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        UnifiedTaskRecord taskRecord = new UnifiedTaskRecord();
        taskRecord.setTaskId(taskId);
        taskRecord.setRequestId(request.getRequestId());
        taskRecord.setDeviceId(request.getDeviceId());
        taskRecord.setCode(request.getCode());
        taskRecord.setOperation(definition.getOperation());
        taskRecord.setMode(definition.getMode());
        taskRecord.setStatus("ACCEPTED");
        taskRecord.setStatusMessage("任务已受理");
        taskRecord.setCreatedAt(new Date());
        unifiedTaskStore.save(taskRecord);

        UnifiedCommandResponseData responseData = UnifiedCommandResponseData.fromTaskRecord(taskRecord);
        asyncExecutor.execute(() -> runAsyncTask(taskId, request, definition, device, executor));
        return responseData;
    }

    private void runAsyncTask(String taskId,
                              UnifiedCommandRequest request,
                              OperationCodeRegistry.OperationDefinition definition,
                              DeviceInfo device,
                              UnifiedCommandExecutor executor) {
        try {
            unifiedTaskStore.markRunning(taskId, "任务执行中");
            UnifiedCommandExecutor.ExecutionResult result = executor.execute(device, request, definition);
            unifiedTaskStore.markFinished(taskId, result.isSuccess(), result.getStatusMessage(), result.getDownstream());
        } catch (Exception e) {
            log.error("统一接口异步任务执行失败: taskId={}, requestId={}, deviceId={}, code={}, operation={}",
                    taskId, request.getRequestId(), request.getDeviceId(), request.getCode(), request.getOperation(), e);
            unifiedTaskStore.markFinished(taskId, false, "任务执行异常: " + e.getMessage(), null);
        }
    }

    private static Executor buildExecutor(int poolSize) {
        return Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "unified-command-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload != null ? new LinkedHashMap<String, Object>(payload) : new LinkedHashMap<String, Object>();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String firstTrimmed = trimToNull(first);
        return firstTrimmed != null ? firstTrimmed : trimToNull(second);
    }
}
