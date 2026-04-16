package com.qasky.qdns.service.unified;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SshOpsExecutorTest {

    @Test
    void shouldReturnParsedBackupPayloadOnSuccess() throws Exception {
        SshCommandRunner runner = mock(SshCommandRunner.class);
        when(runner.execute(anyString(), anyInt(), anyString(), anyString(), eq("backup config"), anyInt(), anyInt()))
                .thenReturn(new SshCommandRunner.CommandResult(0, "{\"version\":\"V1.0.0\"}"));

        SshOpsExecutor executor = new SshOpsExecutor(runner, new ObjectMapper(), 1000, 2000, 128, "", "");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest("backup", Collections.<String, Object>emptyMap()),
                new OperationCodeRegistry().getDefinition(10005, "backup")
        );

        assertTrue(result.isSuccess());
        assertTrue(result.getDownstream() instanceof Map);
        Object backup = ((Map<?, ?>) result.getDownstream()).get("backup");
        assertTrue(backup instanceof Map);
        assertEquals("V1.0.0", ((Map<?, ?>) backup).get("version"));
    }

    @Test
    void shouldReturnTimeoutFailure() throws Exception {
        SshCommandRunner runner = mock(SshCommandRunner.class);
        when(runner.execute(anyString(), anyInt(), anyString(), anyString(), eq("upgrade V3.3.0"), anyInt(), anyInt()))
                .thenThrow(new TimeoutException("connect timeout"));

        SshOpsExecutor executor = new SshOpsExecutor(runner, new ObjectMapper(), 1000, 2000, 128, "", "");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest("upgrade", Collections.<String, Object>singletonMap("version", "V3.3.0")),
                new OperationCodeRegistry().getDefinition(10007, "upgrade")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getStatusMessage().contains("SSH执行超时"));
    }

    @Test
    void shouldReturnAuthFailure() throws Exception {
        SshCommandRunner runner = mock(SshCommandRunner.class);
        when(runner.execute(anyString(), anyInt(), anyString(), anyString(), eq("reboot"), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("auth failed"));

        SshOpsExecutor executor = new SshOpsExecutor(runner, new ObjectMapper(), 1000, 2000, 128, "", "");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest("reboot", Collections.<String, Object>emptyMap()),
                new OperationCodeRegistry().getDefinition(10008, "reboot")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getStatusMessage().contains("auth failed"));
    }

    private static DeviceInfo buildDevice() {
        DeviceInfo device = new DeviceInfo();
        device.setDeviceIp("192.168.1.151");
        device.setSshPort(22);
        device.setSshUsername("vpnadmin");
        device.setSshPassword("vpnadmin123");
        return device;
    }

    private static UnifiedCommandRequest buildRequest(String operation, Map<String, Object> payload) {
        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-" + operation);
        request.setDeviceId("dev-1");
        request.setCode(10005);
        request.setOperation(operation);
        request.setPayload(payload);
        return request;
    }
}
