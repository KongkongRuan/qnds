package com.qasky.qdns.service.unified;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * SSH运维操作执行器
 */
@Component
public class SshOpsExecutor implements UnifiedCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SshOpsExecutor.class);

    private final SshCommandRunner sshCommandRunner;
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int executeTimeoutMs;
    private final int outputLogMaxLength;
    private final String defaultUsername;
    private final String defaultPassword;

    public SshOpsExecutor(SshCommandRunner sshCommandRunner,
                          ObjectMapper objectMapper,
                          @Value("${unified.ssh.timeout.connect:10000}") int connectTimeoutMs,
                          @Value("${unified.ssh.timeout.execute:30000}") int executeTimeoutMs,
                          @Value("${unified.ssh.output-log-max-length:2048}") int outputLogMaxLength,
                          @Value("${unified.ssh.default-username:}") String defaultUsername,
                          @Value("${unified.ssh.default-password:}") String defaultPassword) {
        this.sshCommandRunner = sshCommandRunner;
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.executeTimeoutMs = executeTimeoutMs;
        this.outputLogMaxLength = outputLogMaxLength;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public String executorKey() {
        return "ssh-ops";
    }

    @Override
    public ExecutionResult execute(DeviceInfo device,
                                   UnifiedCommandRequest request,
                                   OperationCodeRegistry.OperationDefinition definition) {
        String username = firstNonBlank(device.getSshUsername(), defaultUsername);
        String password = firstNonBlank(device.getSshPassword(), defaultPassword);
        int sshPort = device.getSshPort() != null ? device.getSshPort() : 22;

        if (username == null || password == null) {
            return ExecutionResult.failed("设备未配置SSH凭据", null);
        }

        String command = buildCommand(definition.getDownstreamAction(), request.getPayload());
        try {
            SshCommandRunner.CommandResult result = sshCommandRunner.execute(
                    device.getDeviceIp(), sshPort, username, password, command, connectTimeoutMs, executeTimeoutMs);

            Integer exitStatus = result.getExitStatus();
            String output = result.getOutput() != null ? result.getOutput().trim() : "";
            boolean success = (exitStatus == null || exitStatus == 0) && !output.startsWith("Error:");

            Map<String, Object> downstream = buildDownstream(definition.getDownstreamAction(), command, output);
            log.info("SSH运维操作完成: requestId={}, deviceId={}, action={}, exitStatus={}, output={}",
                    request.getRequestId(), request.getDeviceId(), definition.getDownstreamAction(),
                    exitStatus, truncateForLog(output));

            return success ? ExecutionResult.success(resolveMessage(definition.getDownstreamAction(), output), downstream)
                    : ExecutionResult.failed(resolveMessage(definition.getDownstreamAction(), output), downstream);
        } catch (TimeoutException e) {
            log.error("SSH运维操作超时: requestId={}, deviceId={}, action={}",
                    request.getRequestId(), request.getDeviceId(), definition.getDownstreamAction(), e);
            return ExecutionResult.failed("SSH执行超时: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("SSH运维操作失败: requestId={}, deviceId={}, action={}",
                    request.getRequestId(), request.getDeviceId(), definition.getDownstreamAction(), e);
            return ExecutionResult.failed("SSH执行失败: " + e.getMessage(), null);
        }
    }

    private String buildCommand(String action, Map<String, Object> payload) {
        if ("backup".equals(action)) {
            return "backup config";
        }
        if ("restore".equals(action)) {
            return "restore config";
        }
        if ("upgrade".equals(action)) {
            return "upgrade " + String.valueOf(payload.get("version"));
        }
        if ("reboot".equals(action)) {
            return "reboot";
        }
        throw new IllegalArgumentException("不支持的SSH运维动作: " + action);
    }

    private Map<String, Object> buildDownstream(String action, String command, String output) {
        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", action);
        downstream.put("command", command);
        if ("backup".equals(action)) {
            downstream.put("backup", tryParseJson(output));
        } else if ("restore".equals(action)) {
            downstream.put("backupDataAccepted", true);
            downstream.put("output", output);
        } else {
            downstream.put("output", output);
        }
        return downstream;
    }

    private Object tryParseJson(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }
        try {
            return objectMapper.readValue(output, Object.class);
        } catch (Exception ignored) {
            return output;
        }
    }

    private String resolveMessage(String action, String output) {
        if (output != null && !output.isEmpty()) {
            return output;
        }
        if ("backup".equals(action)) {
            return "备份完成";
        }
        if ("restore".equals(action)) {
            return "恢复命令执行完成";
        }
        if ("upgrade".equals(action)) {
            return "升级命令执行完成";
        }
        if ("reboot".equals(action)) {
            return "重启命令执行完成";
        }
        return "SSH执行完成";
    }

    private String truncateForLog(String output) {
        if (output == null || output.length() <= outputLogMaxLength) {
            return output;
        }
        return output.substring(0, outputLogMaxLength) + "...";
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }
}
