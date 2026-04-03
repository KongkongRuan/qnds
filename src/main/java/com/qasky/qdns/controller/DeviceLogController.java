package com.qasky.qdns.controller;

import com.qasky.qdns.model.dto.DeviceLogBatchResult;
import com.qasky.qdns.model.dto.DeviceLogFetchRequest;
import com.qasky.qdns.model.dto.DeviceLogFetchResult;
import com.qasky.qdns.service.DeviceLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/logs")
public class DeviceLogController {

    private final DeviceLogService deviceLogService;

    public DeviceLogController(DeviceLogService deviceLogService) {
        this.deviceLogService = deviceLogService;
    }

    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchLogs(@RequestBody DeviceLogFetchRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("deviceId不能为空"));
        }

        DeviceLogFetchResult result = deviceLogService.fetchDeviceLogs(
                request.getDeviceId(),
                request.getCount(),
                request.getSince()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", result.getSuccess() ? 200 : 500);
        response.put("message", result.getSuccess() ? "拉取成功" : result.getErrorMessage());
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> fetchBatchLogs(@RequestBody DeviceLogFetchRequest request) {
        List<String> deviceIds = request.getDeviceIds();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("deviceIds不能为空"));
        }

        DeviceLogBatchResult result = deviceLogService.fetchBatchLogs(
                deviceIds,
                request.getCount(),
                request.getSince()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", "批量拉取完成");
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fetch-all")
    public ResponseEntity<Map<String, Object>> fetchAllLogs(@RequestBody(required = false) DeviceLogFetchRequest request) {
        Integer count = null;
        String since = null;
        if (request != null) {
            count = request.getCount();
            since = request.getSince();
        }

        DeviceLogBatchResult result = deviceLogService.fetchAllDeviceLogs(count, since);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", "拉取全部设备日志完成");
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 400);
        result.put("message", message);
        return result;
    }
}
