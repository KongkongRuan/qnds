package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预留能力执行器
 */
@Component
public class ReservedExecutor implements UnifiedCommandExecutor {

    @Override
    public String executorKey() {
        return "reserved";
    }

    @Override
    public ExecutionResult execute(DeviceInfo device,
                                   UnifiedCommandRequest request,
                                   OperationCodeRegistry.OperationDefinition definition) {
        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("implemented", false);
        downstream.put("capability", definition.getCapabilityName());
        downstream.put("operation", definition.getOperation());
        return ExecutionResult.failed("当前版本未实现该能力", downstream);
    }
}
