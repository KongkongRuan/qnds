package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * VPN-Sim SSL-VPN IPC HTTP执行器
 */
@Component
public class SslVpnIpcExecutor implements UnifiedCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SslVpnIpcExecutor.class);
    private static final String SRC_CHANNEL = "ovpn:channel:web_agent";

    private final RestTemplate restTemplate;
    private final int apiPort;
    private final String addressingMode;

    @Autowired
    public SslVpnIpcExecutor(@Value("${vpn-sim.ssl-vpn.api-port:8443}") int apiPort,
                             @Value("${vpn-sim.ssl-vpn.timeout.connect:5000}") int connectTimeout,
                             @Value("${vpn-sim.ssl-vpn.timeout.read:10000}") int readTimeout,
                             @Value("${vpn-sim.ssl-vpn.addressing-mode:ip}") String addressingMode) {
        this(createRestTemplate(connectTimeout, readTimeout), apiPort, addressingMode);
    }

    SslVpnIpcExecutor(RestTemplate restTemplate, int apiPort, String addressingMode) {
        this.restTemplate = restTemplate;
        this.apiPort = apiPort;
        this.addressingMode = addressingMode;
    }

    @Override
    public String executorKey() {
        return "ssl-vpn-ipc";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(DeviceInfo device,
                                   UnifiedCommandRequest request,
                                   OperationCodeRegistry.OperationDefinition definition) {
        if (!"ip".equalsIgnoreCase(addressingMode)) {
            return ExecutionResult.failed("当前仅支持VPN-Sim addressing.mode=ip", null);
        }

        String dstChannel = resolveDstChannel(definition.getDownstreamAction());
        if (dstChannel == null) {
            return ExecutionResult.failed("未识别的SSL-VPN IPC命令: " + definition.getDownstreamAction(), null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", definition.getDownstreamAction());
        body.put("src_channel", SRC_CHANNEL);
        body.put("dst_channel", dstChannel);
        body.put("session_id", buildSessionId(request.getRequestId()));
        if (request.getPayload() != null) {
            body.putAll(request.getPayload());
        }

        String url = String.format(Locale.ROOT, "http://%s:%d/api/ipc", device.getDeviceIp(), apiPort);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<Map<String, Object>>(body, headers), Map.class);
            if (response == null) {
                return ExecutionResult.failed("SSL-VPN IPC无响应数据", null);
            }
            Object resultCode = response.get("result");
            String message = response.get("message") != null ? String.valueOf(response.get("message")) : "unknown";
            boolean success = "0".equals(String.valueOf(resultCode));
            return success ? ExecutionResult.success(message, response) : ExecutionResult.failed(message, response);
        } catch (RestClientException e) {
            log.error("SSL-VPN IPC调用失败: requestId={}, deviceId={}, url={}", request.getRequestId(), request.getDeviceId(), url, e);
            return ExecutionResult.failed("SSL-VPN IPC调用失败: " + e.getMessage(), null);
        }
    }

    private static RestTemplate createRestTemplate(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    private static Integer buildSessionId(String requestId) {
        int hash = requestId != null ? requestId.hashCode() : 0;
        if (hash == Integer.MIN_VALUE) {
            hash = 1;
        }
        return Math.abs(hash);
    }

    private String resolveDstChannel(String command) {
        if (command == null) {
            return null;
        }
        if (command.contains("web_agent2net_agent")) {
            return "ovpn:channel:net_agent";
        }
        if (command.contains("web_agent2ipsec_agent")) {
            return "ovpn:channel:ipsec_agent";
        }
        if (command.contains("web_agent2user_agent")) {
            return "ovpn:channel:user_agent";
        }
        if (command.contains("web_agent2iec_agent")) {
            return "ovpn:channel:iec_agent";
        }
        return null;
    }
}
