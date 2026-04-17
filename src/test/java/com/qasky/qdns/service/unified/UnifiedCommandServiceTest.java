package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import com.qasky.qdns.model.dto.UnifiedCommandResponseData;
import com.qasky.qdns.model.dto.UnifiedTaskRecord;
import com.qasky.qdns.service.DeviceCollectorService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UnifiedCommandServiceTest {

    @Test
    void shouldRouteSyncCommandToExecutor() {
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        UnifiedCommandExecutor sslExecutor = mock(UnifiedCommandExecutor.class);
        UnifiedTaskStore taskStore = new UnifiedTaskStore(null, "test:task:", 60);
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = buildDevice("dev-1");
        when(deviceCollectorService.getDeviceById("dev-1")).thenReturn(device);
        when(sslExecutor.executorKey()).thenReturn("ssl-vpn-ipc");
        when(sslExecutor.execute(any(DeviceInfo.class), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class)))
                .thenReturn(UnifiedCommandExecutor.ExecutionResult.success("ok", Collections.singletonMap("result", 0)));

        UnifiedCommandService service = new UnifiedCommandService(
                deviceCollectorService,
                registry,
                taskStore,
                Collections.singletonList(sslExecutor),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(10001);
        request.setOperation("add");
        request.setPayload(requiredAclPayload());

        UnifiedCommandResponseData response = service.executeCommand(request);

        assertEquals("SYNC", response.getMode());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("ok", response.getStatusMessage());
        verify(sslExecutor).execute(eq(device), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class));
    }

    @Test
    void shouldRouteSslVpnQueryCommandWithoutPayloadFields() {
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        UnifiedCommandExecutor sslExecutor = mock(UnifiedCommandExecutor.class);
        UnifiedTaskStore taskStore = new UnifiedTaskStore(null, "test:task:", 60);
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = buildDevice("dev-1");
        when(deviceCollectorService.getDeviceById("dev-1")).thenReturn(device);
        when(sslExecutor.executorKey()).thenReturn("ssl-vpn-ipc");
        when(sslExecutor.execute(any(DeviceInfo.class), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class)))
                .thenReturn(UnifiedCommandExecutor.ExecutionResult.success("query ok", Collections.singletonMap("result", 0)));

        UnifiedCommandService service = new UnifiedCommandService(
                deviceCollectorService,
                registry,
                taskStore,
                Collections.singletonList(sslExecutor),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(10000);
        request.setOperation("get_base_nego");
        request.setPayload(Collections.<String, Object>emptyMap());

        UnifiedCommandResponseData response = service.executeCommand(request);

        assertEquals("SYNC", response.getMode());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("query ok", response.getStatusMessage());
        verify(sslExecutor).execute(eq(device), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class));
    }

    @Test
    void shouldRejectUnknownCode() {
        UnifiedCommandService service = new UnifiedCommandService(
                mock(DeviceCollectorService.class),
                new OperationCodeRegistry(),
                new UnifiedTaskStore(null, "test:task:", 60),
                Collections.<UnifiedCommandExecutor>emptyList(),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(99999);
        request.setOperation("add");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.executeCommand(request));
        assertTrue(exception.getMessage().contains("不支持的code"));
    }

    @Test
    void shouldRejectMissingPayloadFields() {
        UnifiedCommandService service = new UnifiedCommandService(
                mock(DeviceCollectorService.class),
                new OperationCodeRegistry(),
                new UnifiedTaskStore(null, "test:task:", 60),
                Collections.<UnifiedCommandExecutor>emptyList(),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(10007);
        request.setOperation("upgrade");
        request.setPayload(new LinkedHashMap<String, Object>());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.executeCommand(request));
        assertTrue(exception.getMessage().contains("version"));
    }

    @Test
    void shouldRejectMissingPagingFieldsForListQuery() {
        UnifiedCommandService service = new UnifiedCommandService(
                mock(DeviceCollectorService.class),
                new OperationCodeRegistry(),
                new UnifiedTaskStore(null, "test:task:", 60),
                Collections.<UnifiedCommandExecutor>emptyList(),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(10004);
        request.setOperation("get_snat_list");
        request.setPayload(new LinkedHashMap<String, Object>());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.executeCommand(request));
        assertTrue(exception.getMessage().contains("page"));
        assertTrue(exception.getMessage().contains("rows"));
    }

    @Test
    void shouldFailWhenDeviceDoesNotExist() {
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        when(deviceCollectorService.getDeviceById("missing")).thenReturn(null);

        UnifiedCommandExecutor vpnSimExecutor = mock(UnifiedCommandExecutor.class);
        when(vpnSimExecutor.executorKey()).thenReturn("vpn-sim-http");

        UnifiedCommandService service = new UnifiedCommandService(
                deviceCollectorService,
                new OperationCodeRegistry(),
                new UnifiedTaskStore(null, "test:task:", 60),
                Collections.singletonList(vpnSimExecutor),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("missing");
        request.setCode(10005);
        request.setOperation("backup");

        assertThrows(NoSuchElementException.class, () -> service.executeCommand(request));
    }

    @Test
    void shouldCreateAsyncTaskAndPersistFinalStatus() {
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        UnifiedCommandExecutor vpnSimExecutor = mock(UnifiedCommandExecutor.class);
        UnifiedTaskStore taskStore = new UnifiedTaskStore(null, "test:task:", 60);
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = buildDevice("dev-1");
        when(deviceCollectorService.getDeviceById("dev-1")).thenReturn(device);
        when(vpnSimExecutor.executorKey()).thenReturn("vpn-sim-http");
        when(vpnSimExecutor.execute(any(DeviceInfo.class), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class)))
                .thenReturn(UnifiedCommandExecutor.ExecutionResult.success("upgrade queued", Collections.singletonMap("action", "upgrade")));

        UnifiedCommandService service = new UnifiedCommandService(
                deviceCollectorService,
                registry,
                taskStore,
                Collections.singletonList(vpnSimExecutor),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-async");
        request.setDeviceId("dev-1");
        request.setCode(10007);
        request.setOperation("upgrade");
        request.setPayload(Collections.<String, Object>singletonMap("version", "V3.3.0"));

        UnifiedCommandResponseData response = service.executeCommand(request);
        assertEquals("ASYNC", response.getMode());
        assertEquals("ACCEPTED", response.getStatus());
        assertNotNull(response.getTaskId());

        UnifiedTaskRecord taskRecord = taskStore.get(response.getTaskId());
        assertEquals("SUCCESS", taskRecord.getStatus());
        assertEquals("upgrade queued", taskRecord.getStatusMessage());
        assertNotNull(taskRecord.getFinishedAt());
    }

    @Test
    void shouldRouteLegacyUploadRootCertAliasToSslExecutor() {
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceInfo device = buildDevice("dev-1");
        when(deviceCollectorService.getDeviceById("dev-1")).thenReturn(device);

        UnifiedCommandExecutor sslExecutor = mock(UnifiedCommandExecutor.class);
        when(sslExecutor.executorKey()).thenReturn("ssl-vpn-ipc");
        when(sslExecutor.execute(any(DeviceInfo.class), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class)))
                .thenReturn(UnifiedCommandExecutor.ExecutionResult.success("ok", Collections.singletonMap("result", 0)));

        UnifiedCommandService service = new UnifiedCommandService(
                deviceCollectorService,
                new OperationCodeRegistry(),
                new UnifiedTaskStore(null, "test:task:", 60),
                Arrays.<UnifiedCommandExecutor>asList(sslExecutor),
                directExecutor()
        );

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId("dev-1");
        request.setCode(10009);
        request.setOperation("upload_root_cert");
        request.setPayload(Collections.<String, Object>emptyMap());

        UnifiedCommandResponseData response = service.executeCommand(request);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("upload_root_cert", response.getOperation());
        verify(sslExecutor).execute(eq(device), any(UnifiedCommandRequest.class), any(OperationCodeRegistry.OperationDefinition.class));
    }

    private static DeviceInfo buildDevice(String deviceId) {
        DeviceInfo device = new DeviceInfo();
        device.setId(deviceId);
        device.setDeviceIp("192.168.1.151");
        device.setSshPort(22);
        device.setSshUsername("vpnadmin");
        device.setSshPassword("vpnadmin123");
        return device;
    }

    private static Map<String, Object> requiredAclPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "acl-1");
        payload.put("action", "allow");
        payload.put("protocol", "tcp");
        payload.put("src_addr", "10.0.0.1");
        payload.put("dst_addr", "10.0.0.2");
        payload.put("time_limit_state", 0);
        payload.put("begin_time", "08:00");
        payload.put("end_time", "18:00");
        return payload;
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }
}
