package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VpnSimHttpExecutorTest {

    @Test
    void shouldReturnZipBase64OnBackup() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        byte[] archive = "zip-demo".getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"device-0001.zip\"");

        server.expect(requestTo("http://192.168.1.151:8888/api/devices/by-address/backup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"ip_address\":\"192.168.1.151\",\"snmp_port\":161}"))
                .andRespond(withSuccess(archive, MediaType.parseMediaType("application/zip")).headers(headers));

        VpnSimHttpExecutor executor = new VpnSimHttpExecutor(restTemplate, 8888, 200L, 5000L, "ip");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest(10005, "backup", Collections.<String, Object>emptyMap()),
                new OperationCodeRegistry().getDefinition(10005, "backup")
        );

        assertTrue(result.isSuccess());
        assertTrue(result.getDownstream() instanceof Map);
        Map<?, ?> downstream = (Map<?, ?>) result.getDownstream();
        assertEquals("device-0001.zip", downstream.get("filename"));
        assertEquals(Base64.getEncoder().encodeToString(archive), downstream.get("zipBase64"));
        server.verify();
    }

    @Test
    void shouldTranslateRestorePayloadToVpnSimFormat() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("http://192.168.1.151:8888/api/devices/by-address/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"ip_address\":\"192.168.1.151\",\"snmp_port\":161,\"zip_base64\":\"UEsDBA==\",\"filename\":\"device-0001.zip\"}"))
                .andRespond(withSuccess("{\"message\":\"配置已恢复\",\"firmware_version\":\"V3.3.0\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zipBase64", "UEsDBA==");
        payload.put("filename", "device-0001.zip");

        VpnSimHttpExecutor executor = new VpnSimHttpExecutor(restTemplate, 8888, 200L, 5000L, "ip");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest(10006, "restore", payload),
                new OperationCodeRegistry().getDefinition(10006, "restore")
        );

        assertTrue(result.isSuccess());
        assertEquals("配置已恢复", result.getStatusMessage());
        assertTrue(result.getDownstream() instanceof Map);
        assertEquals("V3.3.0", ((Map<?, ?>) result.getDownstream()).get("firmware_version"));
        server.verify();
    }

    @Test
    void shouldPollUntilUpgradeVersionMatches() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("http://192.168.1.151:8888/api/devices/by-address/upgrade"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"ip_address\":\"192.168.1.151\",\"snmp_port\":161,\"version\":\"V4.0.0-build20260415\"}"))
                .andRespond(withSuccess("{\"message\":\"设备 0001 正在升级\"}", MediaType.APPLICATION_JSON));

        server.expect(times(1), requestTo("http://192.168.1.151:8888/api/devices/by-address?ip_address=192.168.1.151&snmp_port=161"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":2,\"firmware_version\":\"V3.2.0-build20260401\"}", MediaType.APPLICATION_JSON));

        server.expect(times(1), requestTo("http://192.168.1.151:8888/api/devices/by-address?ip_address=192.168.1.151&snmp_port=161"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":1,\"firmware_version\":\"V4.0.0-build20260415\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "V4.0.0-build20260415");

        VpnSimHttpExecutor executor = new VpnSimHttpExecutor(restTemplate, 8888, 200L, 5000L, "ip");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest(10007, "upgrade", payload),
                new OperationCodeRegistry().getDefinition(10007, "upgrade")
        );

        assertTrue(result.isSuccess());
        assertTrue(result.getStatusMessage().contains("V4.0.0-build20260415"));
        assertTrue(result.getDownstream() instanceof Map);
        assertEquals("V4.0.0-build20260415", ((Map<?, ?>) result.getDownstream()).get("firmwareVersion"));
        server.verify();
    }

    private static DeviceInfo buildDevice() {
        DeviceInfo device = new DeviceInfo();
        device.setId("qdms-device-0001");
        device.setDeviceIp("192.168.1.151");
        device.setDevicePort("161");
        return device;
    }

    private static UnifiedCommandRequest buildRequest(int code, String operation, Map<String, Object> payload) {
        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-" + operation);
        request.setDeviceId("qdms-device-0001");
        request.setCode(code);
        request.setOperation(operation);
        request.setPayload(payload);
        return request;
    }
}
