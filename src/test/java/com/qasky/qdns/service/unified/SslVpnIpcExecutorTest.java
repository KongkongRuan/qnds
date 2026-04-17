package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SslVpnIpcExecutorTest {

    @Test
    void shouldMapSuccessfulIpcResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SslVpnIpcExecutor executor = new SslVpnIpcExecutor(restTemplate, 8443, "ip");
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = new DeviceInfo();
        device.setDeviceIp("192.168.1.151");

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-1");
        request.setDeviceId("dev-1");
        request.setCode(10001);
        request.setOperation("add");
        request.setPayload(requiredAclPayload());

        int sessionId = Math.abs("req-1".hashCode());
        String expectedBody = "{"
                + "\"command\":\"ipc:req:web_agent2net_agent:add_acl\","
                + "\"src_channel\":\"ovpn:channel:web_agent\","
                + "\"dst_channel\":\"ovpn:channel:net_agent\","
                + "\"session_id\":" + sessionId + ","
                + "\"name\":\"acl-1\","
                + "\"action\":\"allow\","
                + "\"protocol\":\"tcp\","
                + "\"src_addr\":\"10.0.0.1\","
                + "\"dst_addr\":\"10.0.0.2\","
                + "\"time_limit_state\":0,"
                + "\"begin_time\":\"08:00\","
                + "\"end_time\":\"18:00\""
                + "}";

        server.expect(requestTo("http://192.168.1.151:8443/api/ipc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody, false))
                .andRespond(withSuccess("{\"result\":0,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                device,
                request,
                registry.getDefinition(10001, "add")
        );

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getStatusMessage());
        assertTrue(result.getDownstream() instanceof Map);
        assertEquals(0, ((Map<?, ?>) result.getDownstream()).get("result"));
        server.verify();
    }

    @Test
    void shouldRejectNonIpAddressingMode() {
        SslVpnIpcExecutor executor = new SslVpnIpcExecutor(new RestTemplate(), 8443, "port");
        DeviceInfo device = new DeviceInfo();
        device.setDeviceIp("127.0.0.1");

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-port");
        request.setPayload(requiredAclPayload());

        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                device,
                request,
                new OperationCodeRegistry().getDefinition(10001, "add")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getStatusMessage().contains("addressing.mode=ip"));
    }

    @Test
    void shouldRouteCertificateCommandsToIpsecChannel() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SslVpnIpcExecutor executor = new SslVpnIpcExecutor(restTemplate, 8443, "ip");
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = new DeviceInfo();
        device.setDeviceIp("192.168.1.151");

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-cert");
        request.setDeviceId("dev-1");
        request.setCode(10011);
        request.setOperation("query_cert_status");

        int sessionId = Math.abs("req-cert".hashCode());
        String expectedBody = "{"
                + "\"command\":\"ipc:req:web_agent2ipsec_agent:query_cert_status\","
                + "\"src_channel\":\"ovpn:channel:web_agent\","
                + "\"dst_channel\":\"ovpn:channel:ipsec_agent\","
                + "\"session_id\":" + sessionId
                + "}";

        server.expect(requestTo("http://192.168.1.151:8443/api/ipc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody, false))
                .andRespond(withSuccess("{\"result\":0,\"message\":\"success\"}", MediaType.APPLICATION_JSON));

        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                device,
                request,
                registry.getDefinition(10011, "query_cert_status")
        );

        assertTrue(result.isSuccess());
        server.verify();
    }

    @Test
    void shouldRouteListQueryCommandsToNetChannel() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SslVpnIpcExecutor executor = new SslVpnIpcExecutor(restTemplate, 8443, "ip");
        OperationCodeRegistry registry = new OperationCodeRegistry();

        DeviceInfo device = new DeviceInfo();
        device.setDeviceIp("192.168.1.151");

        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setRequestId("req-acl-list");
        request.setDeviceId("dev-1");
        request.setCode(10001);
        request.setOperation("get_acl_list");
        request.setPayload(listPayload());

        int sessionId = Math.abs("req-acl-list".hashCode());
        String expectedBody = "{"
                + "\"command\":\"ipc:req:web_agent2net_agent:get_acl_list\","
                + "\"src_channel\":\"ovpn:channel:web_agent\","
                + "\"dst_channel\":\"ovpn:channel:net_agent\","
                + "\"session_id\":" + sessionId + ","
                + "\"page\":1,"
                + "\"rows\":20"
                + "}";

        server.expect(requestTo("http://192.168.1.151:8443/api/ipc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody, false))
                .andRespond(withSuccess("{\"result\":0,\"message\":\"success\",\"acl_count\":0}", MediaType.APPLICATION_JSON));

        UnifiedCommandExecutor.ExecutionResult result = executor.execute(
                device,
                request,
                registry.getDefinition(10001, "get_acl_list")
        );

        assertTrue(result.isSuccess());
        server.verify();
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

    private static Map<String, Object> listPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", 1);
        payload.put("rows", 20);
        return payload;
    }
}
