package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一运维执行器抽象
 */
public interface UnifiedCommandExecutor {

    String executorKey();

    ExecutionResult execute(DeviceInfo device,
                            UnifiedCommandRequest request,
                            OperationCodeRegistry.OperationDefinition definition);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ExecutionResult {
        private boolean success;
        private String statusMessage;
        private Object downstream;

        public static ExecutionResult success(String statusMessage, Object downstream) {
            return new ExecutionResult(true, statusMessage, downstream);
        }

        public static ExecutionResult failed(String statusMessage, Object downstream) {
            return new ExecutionResult(false, statusMessage, downstream);
        }
    }
}
