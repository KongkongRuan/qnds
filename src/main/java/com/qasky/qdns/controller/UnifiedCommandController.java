package com.qasky.qdns.controller;

import com.qasky.qdns.model.ApiResponse;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import com.qasky.qdns.model.dto.UnifiedCommandResponseData;
import com.qasky.qdns.model.dto.UnifiedTaskRecord;
import com.qasky.qdns.service.unified.UnifiedCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * 统一运维接口
 */
@RestController
@RequestMapping("/api/unified")
public class UnifiedCommandController {

    private static final Logger log = LoggerFactory.getLogger(UnifiedCommandController.class);

    private final UnifiedCommandService unifiedCommandService;

    public UnifiedCommandController(UnifiedCommandService unifiedCommandService) {
        this.unifiedCommandService = unifiedCommandService;
    }

    @PostMapping("/command")
    public ResponseEntity<ApiResponse<UnifiedCommandResponseData>> executeCommand(
            @RequestBody(required = false) UnifiedCommandRequest request) {
        try {
            UnifiedCommandResponseData responseData = unifiedCommandService.executeCommand(request);
            return ResponseEntity.ok(ApiResponse.ok(responseData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            log.error("统一接口执行异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "统一接口执行异常: " + e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<UnifiedTaskRecord>> getTask(@PathVariable String taskId) {
        return buildTaskResponse(taskId);
    }

    @GetMapping("/task")
    public ResponseEntity<ApiResponse<UnifiedTaskRecord>> getTaskByParam(@RequestParam("taskId") String taskId) {
        return buildTaskResponse(taskId);
    }

    private ResponseEntity<ApiResponse<UnifiedTaskRecord>> buildTaskResponse(String taskId) {
        try {
            UnifiedTaskRecord taskRecord = unifiedCommandService.getTask(taskId);
            return ResponseEntity.ok(ApiResponse.ok(taskRecord));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            log.error("统一接口任务查询异常: taskId={}", taskId, e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "统一接口任务查询异常: " + e.getMessage()));
        }
    }
}
