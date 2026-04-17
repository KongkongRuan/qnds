package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.client.MockClientHttpRequest;
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
    void shouldReplaceAllAclViaSingleRestoreRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("http://192.168.1.151:8888/api/devices/by-address/ssl-vpn-state?ip_address=192.168.1.151&snmp_port=161"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(buildCurrentSslVpnState(), MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://192.168.1.151:8888/api/devices/by-address/restore"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("\"filename\":\"qdns-acls-replace-all.json\""));
                    assertTrue(body.contains("\"whitelist_state\":1"));
                    assertTrue(body.contains("\"id\":\"acl-0001\""));
                    assertTrue(body.contains("\"name\":\"acl-new\""));
                    assertFalse(body.contains("\"name\":\"acl-old\""));
                })
                .andRespond(withSuccess("{\"message\":\"配置已恢复\",\"firmware_version\":\"V3.3.0\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", Collections.<Object>singletonList(requiredAclItem()));

        VpnSimHttpExecutor executor = new VpnSimHttpExecutor(restTemplate, 8888, 200L, 5000L, "ip");
        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                buildDevice(),
                buildRequest(10001, "replace_all", payload),
                new OperationCodeRegistry().getDefinition(10001, "replace_all")
        );

        assertTrue(result.isSuccess());
        assertTrue(result.getStatusMessage().contains("ACL配置已整体替换"));
        assertTrue(result.getDownstream() instanceof Map);
        Map<?, ?> downstream = (Map<?, ?>) result.getDownstream();
        assertEquals("replace_all_acl", downstream.get("action"));
        assertEquals("acls", downstream.get("targetField"));
        assertEquals(1, downstream.get("itemsCount"));
        assertEquals("V3.3.0", downstream.get("firmwareVersion"));
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

    private static Map<String, Object> requiredAclItem() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "acl-new");
        payload.put("action", "allow");
        payload.put("protocol", "tcp");
        payload.put("src_addr", "10.0.0.1");
        payload.put("dst_addr", "10.0.0.2");
        payload.put("time_limit_state", 0);
        payload.put("begin_time", "08:00");
        payload.put("end_time", "18:00");
        return payload;
    }

    private static String buildCurrentSslVpnState() {
        return "{"
                + "\"meta\":{\"ip_address\":\"192.168.1.151\",\"device_id\":\"vpn-0001\",\"device_type\":\"quantum_vpn\",\"model\":\"QV-2000\",\"vendor\":\"QASKY\",\"firmware_version\":\"V3.3.0\"},"
                + "\"user_agent\":{\"users\":[]},"
                + "\"iec_agent\":{\"serial_configs\":[],\"tcp_type\":1,\"tcp_server\":{\"tcp_port\":2404},\"tcp_client\":{\"server_ip\":\"\",\"server_port\":2404,\"client_id\":\"\"}},"
                + "\"net_agent\":{\"interfaces\":[],\"routes\":[],\"whitelist_state\":1,"
                + "\"whitelists\":[{\"id\":\"white-0009\",\"name\":\"wl-old\",\"type\":0,\"addr\":\"10.0.0.10\",\"state\":0}],"
                + "\"acls\":[{\"id\":\"acl-0009\",\"name\":\"acl-old\",\"action\":\"deny\",\"protocol\":\"udp\",\"src_addr\":\"1.1.1.1\",\"dst_addr\":\"2.2.2.2\",\"time_limit_state\":1,\"begin_time\":\"00:00\",\"end_time\":\"23:59\"}],"
                + "\"snats\":[],\"dnats\":[]},"
                + "\"ipsec_agent\":{\"base_nego\":{\"interface_name\":\"eth0\",\"local_port\":500,\"local_port_nat\":4500,\"ike_ver\":\"IKEv2\",\"auth_type\":\"1\"},"
                + "\"anonymous_nego\":{\"status\":\"disable\",\"ph1_algs\":\"SM4+SM2+SM3\",\"ph2_algs\":\"SM4+SM2+SM3\",\"ph1_ttl_range\":\"3600\",\"ph2_ttl_range\":\"600\",\"encap_protocol\":\"ESP\",\"encap_mode\":\"tunnel\"},"
                + "\"policies\":[],\"tunnels\":[],"
                + "\"certificate\":{\"source\":\"\",\"algorithm\":\"\",\"hasPrivateKey\":false,\"hasCsr\":false,\"csrPem\":\"\",\"csrSubject\":\"\",\"privateKeyPem\":\"\",\"deviceCertPem\":\"\",\"deviceCertSerial\":\"\",\"deviceCertFingerprint\":\"\",\"deviceCertSubject\":\"\",\"deviceCertIssuer\":\"\",\"deviceCertNotBefore\":\"\",\"deviceCertNotAfter\":\"\",\"caChain\":[],\"updatedAt\":0}}"
                + "}";
    }
}
