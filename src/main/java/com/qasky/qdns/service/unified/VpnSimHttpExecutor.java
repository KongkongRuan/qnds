package com.qasky.qdns.service.unified;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * VPN-Sim HTTP 运维执行器。
 */
@Component
public class VpnSimHttpExecutor implements UnifiedCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(VpnSimHttpExecutor.class);
    private static final String ACTION_REPLACE_ALL_ACL = "replace_all_acl";
    private static final String ACTION_REPLACE_ALL_ROUTE = "replace_all_route";
    private static final String ACTION_REPLACE_ALL_WHITELIST = "replace_all_whitelist";
    private static final String ACTION_REPLACE_ALL_SNAT = "replace_all_snat";
    private static final String ACTION_REPLACE_ALL_DNAT = "replace_all_dnat";

    private final RestTemplate restTemplate;
    private final int apiPort;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;
    private final String addressingMode;

    @Autowired
    public VpnSimHttpExecutor(@Value("${vpn-sim.api.port:8888}") int apiPort,
                              @Value("${vpn-sim.api.timeout.connect:5000}") int connectTimeout,
                              @Value("${vpn-sim.api.timeout.read:30000}") int readTimeout,
                              @Value("${vpn-sim.api.poll.interval-ms:1000}") long pollIntervalMs,
                              @Value("${vpn-sim.api.poll.timeout-ms:180000}") long pollTimeoutMs,
                              @Value("${vpn-sim.ssl-vpn.addressing-mode:ip}") String addressingMode) {
        this(createRestTemplate(connectTimeout, readTimeout), apiPort, pollIntervalMs, pollTimeoutMs, addressingMode);
    }

    VpnSimHttpExecutor(RestTemplate restTemplate,
                       int apiPort,
                       long pollIntervalMs,
                       long pollTimeoutMs,
                       String addressingMode) {
        this.restTemplate = restTemplate;
        this.apiPort = apiPort;
        this.pollIntervalMs = Math.max(200L, pollIntervalMs);
        this.pollTimeoutMs = Math.max(this.pollIntervalMs, pollTimeoutMs);
        this.addressingMode = addressingMode;
    }

    @Override
    public String executorKey() {
        return "vpn-sim-http";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(DeviceInfo device,
                                   UnifiedCommandRequest request,
                                   OperationCodeRegistry.OperationDefinition definition) {
        if (!"ip".equalsIgnoreCase(addressingMode)) {
            return ExecutionResult.failed("当前仅支持VPN-Sim addressing.mode=ip", null);
        }
        if (device == null || isBlank(device.getDeviceIp())) {
            return ExecutionResult.failed("设备缺少VPN-Sim HTTP调用所需的IP", null);
        }

        int snmpPort = resolveSnmpPort(device);
        Map<String, Object> targetPayload = buildTargetPayload(device.getDeviceIp(), snmpPort);

        String action = definition.getDownstreamAction();
        String actionBaseUrl = String.format(Locale.ROOT,
                "http://%s:%d/api/devices/by-address",
                device.getDeviceIp(),
                apiPort);
        String infoUrl = String.format(Locale.ROOT,
                "http://%s:%d/api/devices/by-address?ip_address=%s&snmp_port=%d",
                device.getDeviceIp(),
                apiPort,
                device.getDeviceIp(),
                snmpPort);
        String sslVpnStateUrl = String.format(Locale.ROOT,
                "http://%s:%d/api/devices/by-address/ssl-vpn-state?ip_address=%s&snmp_port=%d",
                device.getDeviceIp(),
                apiPort,
                device.getDeviceIp(),
                snmpPort);

        try {
            if ("backup".equals(action)) {
                return executeBackup(actionBaseUrl, targetPayload);
            }
            if ("restore".equals(action)) {
                return executeRestore(actionBaseUrl, targetPayload, request.getPayload());
            }
            if ("upgrade".equals(action)) {
                return executeUpgrade(actionBaseUrl, infoUrl, targetPayload, request.getPayload());
            }
            if ("reboot".equals(action)) {
                return executeReboot(actionBaseUrl, infoUrl, targetPayload);
            }
            if (isReplaceAllAction(action)) {
                return executeReplaceAll(actionBaseUrl, sslVpnStateUrl, targetPayload, request.getPayload(), action);
            }
            return ExecutionResult.failed("不支持的VPN-Sim HTTP动作: " + action, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failed("任务执行被中断", null);
        } catch (HttpStatusCodeException e) {
            log.error("VPN-Sim HTTP调用失败: requestId={}, deviceId={}, action={}, status={}, body={}",
                    request.getRequestId(), request.getDeviceId(), action, e.getStatusCode(), e.getResponseBodyAsString(), e);
            return ExecutionResult.failed(buildHttpErrorMessage(e), null);
        } catch (RestClientException e) {
            log.error("VPN-Sim HTTP调用异常: requestId={}, deviceId={}, action={}, url={}",
                    request.getRequestId(), request.getDeviceId(), action, actionBaseUrl, e);
            return ExecutionResult.failed("VPN-Sim HTTP调用失败: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("VPN-Sim HTTP执行异常: requestId={}, deviceId={}, action={}, url={}",
                    request.getRequestId(), request.getDeviceId(), action, actionBaseUrl, e);
            return ExecutionResult.failed("VPN-Sim HTTP执行异常: " + e.getMessage(), null);
        }
    }

    private ExecutionResult executeBackup(String actionBaseUrl, Map<String, Object> targetPayload) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                actionBaseUrl + "/backup",
                HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(targetPayload, jsonHeaders()),
                byte[].class
        );
        byte[] archive = response.getBody() != null ? response.getBody() : new byte[0];
        String filename = resolveFilename(response.getHeaders());

        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", "backup");
        downstream.put("filename", filename);
        downstream.put("contentType", response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : "application/zip");
        downstream.put("zipBase64", Base64.getEncoder().encodeToString(archive));
        downstream.put("size", archive.length);

        return ExecutionResult.success("备份完成", downstream);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeRestore(String actionBaseUrl,
                                           Map<String, Object> targetPayload,
                                           Map<String, Object> payload) {
        String zipBase64 = stringValue(payload != null ? payload.get("zipBase64") : null);
        Map<String, Object> configData = mapValue(payload != null ? payload.get("configData") : null);
        if (configData == null || configData.isEmpty()) {
            configData = mapValue(payload != null ? payload.get("config_data") : null);
        }
        String filename = stringValue(payload != null ? payload.get("filename") : null);

        Map<String, Object> response;
        try {
            response = postRestore(actionBaseUrl, targetPayload, zipBase64, configData, filename);
        } catch (IllegalArgumentException e) {
            return ExecutionResult.failed(e.getMessage(), null);
        }

        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", "restore");
        if (response != null) {
            downstream.putAll(response);
        }
        return ExecutionResult.success(resolveMessage(response, "配置已恢复"), downstream);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeUpgrade(String actionBaseUrl,
                                           String infoUrl,
                                           Map<String, Object> targetPayload,
                                           Map<String, Object> payload) throws InterruptedException {
        String targetVersion = stringValue(payload != null ? payload.get("version") : null);
        if (isBlank(targetVersion)) {
            return ExecutionResult.failed("升级操作缺少version", null);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>(targetPayload);
        requestBody.put("version", targetVersion);
        Map<String, Object> response = restTemplate.postForObject(
                actionBaseUrl + "/upgrade",
                new HttpEntity<Map<String, Object>>(requestBody, jsonHeaders()),
                Map.class
        );

        PollOutcome pollOutcome = pollDeviceInfo(infoUrl, new DeviceStateMatcher() {
            @Override
            public boolean matched(Map<String, Object> deviceInfo) {
                return targetVersion.equals(stringValue(deviceInfo.get("firmware_version")));
            }
        });

        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", "upgrade");
        downstream.put("targetVersion", targetVersion);
        if (response != null) {
            downstream.put("acceptedResponse", response);
        }
        if (pollOutcome.deviceInfo != null) {
            downstream.put("deviceInfo", pollOutcome.deviceInfo);
        }

        if (!pollOutcome.matched) {
            return ExecutionResult.failed("升级已受理，但在超时时间内未观察到目标版本: " + targetVersion, downstream);
        }
        downstream.put("firmwareVersion", stringValue(pollOutcome.deviceInfo.get("firmware_version")));
        return ExecutionResult.success("升级完成，当前版本: " + targetVersion, downstream);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeReboot(String actionBaseUrl,
                                          String infoUrl,
                                          Map<String, Object> targetPayload) throws InterruptedException {
        Map<String, Object> response = restTemplate.postForObject(
                actionBaseUrl + "/reboot",
                new HttpEntity<Map<String, Object>>(targetPayload, jsonHeaders()),
                Map.class
        );

        PollOutcome pollOutcome = pollDeviceInfo(infoUrl, new DeviceStateMatcher() {
            @Override
            public boolean matched(Map<String, Object> deviceInfo) {
                Object status = deviceInfo.get("status");
                return status != null && "1".equals(String.valueOf(status));
            }
        });

        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", "reboot");
        if (response != null) {
            downstream.put("acceptedResponse", response);
        }
        if (pollOutcome.deviceInfo != null) {
            downstream.put("deviceInfo", pollOutcome.deviceInfo);
        }

        if (!pollOutcome.matched) {
            return ExecutionResult.failed("重启已受理，但设备未在超时时间内恢复在线", downstream);
        }
        return ExecutionResult.success("设备重启完成", downstream);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeReplaceAll(String actionBaseUrl,
                                              String sslVpnStateUrl,
                                              Map<String, Object> targetPayload,
                                              Map<String, Object> payload,
                                              String action) {
        ReplaceTarget target = resolveReplaceTarget(action);
        if (target == null) {
            return ExecutionResult.failed("不支持的replace_all动作: " + action, null);
        }

        List<Map<String, Object>> normalizedItems;
        try {
            normalizedItems = normalizeReplaceItems(target, payload != null ? payload.get("items") : null);
        } catch (IllegalArgumentException e) {
            return ExecutionResult.failed(e.getMessage(), null);
        }

        Map<String, Object> currentState = restTemplate.getForObject(sslVpnStateUrl, Map.class);
        if (currentState == null || currentState.isEmpty()) {
            return ExecutionResult.failed("获取SSL VPN整状态失败", null);
        }

        Map<String, Object> configData = new LinkedHashMap<>(currentState);
        Map<String, Object> netAgent = mapValue(configData.get("net_agent"));
        if (netAgent == null) {
            return ExecutionResult.failed("当前SSL VPN状态缺少net_agent", null);
        }
        netAgent.put(target.stateField, normalizedItems);
        configData.put("net_agent", netAgent);

        Map<String, Object> response = postRestore(
                actionBaseUrl,
                targetPayload,
                null,
                configData,
                buildReplaceFilename(target)
        );

        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("action", action);
        downstream.put("targetField", target.stateField);
        downstream.put("itemsCount", normalizedItems.size());
        if (response != null) {
            downstream.put("restoreResponse", response);
            Object firmwareVersion = response.get("firmware_version");
            if (firmwareVersion != null) {
                downstream.put("firmwareVersion", firmwareVersion);
            }
        }
        return ExecutionResult.success(target.label + "已整体替换，条目数: " + normalizedItems.size(), downstream);
    }

    @SuppressWarnings("unchecked")
    private PollOutcome pollDeviceInfo(String baseUrl, DeviceStateMatcher matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        Map<String, Object> lastDeviceInfo = null;

        while (System.currentTimeMillis() <= deadline) {
            try {
                lastDeviceInfo = restTemplate.getForObject(baseUrl, Map.class);
                if (lastDeviceInfo != null && matcher.matched(lastDeviceInfo)) {
                    return new PollOutcome(true, lastDeviceInfo);
                }
            } catch (RestClientException e) {
                log.debug("轮询VPN-Sim设备状态失败，继续重试: url={}, error={}", baseUrl, e.getMessage());
            }
            Thread.sleep(pollIntervalMs);
        }
        return new PollOutcome(false, lastDeviceInfo);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String resolveFilename(HttpHeaders headers) {
        if (headers == null) {
            return "device-backup.zip";
        }
        try {
            if (headers.getContentDisposition() != null && !isBlank(headers.getContentDisposition().getFilename())) {
                return headers.getContentDisposition().getFilename();
            }
        } catch (Exception ignored) {
        }
        String contentDisposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (!isBlank(contentDisposition)) {
            int index = contentDisposition.indexOf("filename=");
            if (index >= 0) {
                String filename = contentDisposition.substring(index + "filename=".length()).trim();
                if (filename.startsWith("\"") && filename.endsWith("\"") && filename.length() > 1) {
                    filename = filename.substring(1, filename.length() - 1);
                }
                if (!isBlank(filename)) {
                    return filename;
                }
            }
        }
        return "device-backup.zip";
    }

    private String resolveMessage(Map<String, Object> response, String defaultMessage) {
        if (response == null) {
            return defaultMessage;
        }
        String message = stringValue(response.get("message"));
        return isBlank(message) ? defaultMessage : message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postRestore(String actionBaseUrl,
                                            Map<String, Object> targetPayload,
                                            String zipBase64,
                                            Map<String, Object> configData,
                                            String filename) {
        Map<String, Object> requestBody = new LinkedHashMap<>(targetPayload);
        if (configData != null && !configData.isEmpty()) {
            requestBody.put("config_data", configData);
        } else if (!isBlank(zipBase64)) {
            requestBody.put("zip_base64", zipBase64);
        } else {
            throw new IllegalArgumentException("恢复操作缺少zipBase64或configData");
        }

        if (!isBlank(filename)) {
            requestBody.put("filename", filename);
        }

        return restTemplate.postForObject(
                actionBaseUrl + "/restore",
                new HttpEntity<Map<String, Object>>(requestBody, jsonHeaders()),
                Map.class
        );
    }

    private String buildHttpErrorMessage(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        if (!isBlank(body)) {
            return "VPN-Sim HTTP调用失败(" + e.getRawStatusCode() + "): " + body;
        }
        return "VPN-Sim HTTP调用失败(" + e.getRawStatusCode() + "): " + e.getStatusText();
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return new LinkedHashMap<String, Object>((Map<String, Object>) value);
    }

    private static boolean isReplaceAllAction(String action) {
        return ACTION_REPLACE_ALL_ACL.equals(action)
                || ACTION_REPLACE_ALL_ROUTE.equals(action)
                || ACTION_REPLACE_ALL_WHITELIST.equals(action)
                || ACTION_REPLACE_ALL_SNAT.equals(action)
                || ACTION_REPLACE_ALL_DNAT.equals(action);
    }

    private static ReplaceTarget resolveReplaceTarget(String action) {
        if (ACTION_REPLACE_ALL_ACL.equals(action)) {
            return new ReplaceTarget(
                    "ACL配置",
                    "acls",
                    "acl",
                    new String[]{"name", "action", "protocol", "src_addr", "dst_addr", "time_limit_state", "begin_time", "end_time"},
                    new String[]{"name", "action", "protocol", "src_addr", "dst_addr", "time_limit_state", "begin_time", "end_time"}
            );
        }
        if (ACTION_REPLACE_ALL_ROUTE.equals(action)) {
            return new ReplaceTarget(
                    "路由配置",
                    "routes",
                    null,
                    new String[]{"dst_addr", "mask", "next_ip", "interface_name", "weight", "distance"},
                    new String[]{"dst_addr", "mask", "next_ip", "interface_name", "weight", "distance", "type"}
            );
        }
        if (ACTION_REPLACE_ALL_WHITELIST.equals(action)) {
            return new ReplaceTarget(
                    "白名单配置",
                    "whitelists",
                    "white",
                    new String[]{"name", "type", "addr", "state"},
                    new String[]{"name", "type", "addr", "state"}
            );
        }
        if (ACTION_REPLACE_ALL_SNAT.equals(action)) {
            return new ReplaceTarget(
                    "SNAT配置",
                    "snats",
                    "snat",
                    new String[]{"name", "src_addr", "dst_addr"},
                    new String[]{"name", "src_addr", "dst_addr"}
            );
        }
        if (ACTION_REPLACE_ALL_DNAT.equals(action)) {
            return new ReplaceTarget(
                    "DNAT配置",
                    "dnats",
                    "dnat",
                    new String[]{"name", "protocol", "local_addr", "local_port", "external_addr", "external_port"},
                    new String[]{"name", "protocol", "local_addr", "local_port", "external_addr", "external_port"}
            );
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeReplaceItems(ReplaceTarget target, Object rawItems) {
        if (!(rawItems instanceof List)) {
            throw new IllegalArgumentException(target.label + " replace_all 需要payload.items数组");
        }

        List<?> items = (List<?>) rawItems;
        List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
        long createdAtBase = System.currentTimeMillis() / 1000L;

        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(target.label + " 第" + (index + 1) + "项必须是对象");
            }

            Map<String, Object> rawItem = new LinkedHashMap<String, Object>((Map<String, Object>) item);
            for (String field : target.requiredFields) {
                Object value = rawItem.get(field);
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    throw new IllegalArgumentException(target.label + " 第" + (index + 1) + "项缺少字段: " + field);
                }
            }

            Map<String, Object> normalizedItem = new LinkedHashMap<String, Object>();
            for (String field : target.allowedFields) {
                if (rawItem.containsKey(field)) {
                    normalizedItem.put(field, rawItem.get(field));
                }
            }
            if ("routes".equals(target.stateField) && !normalizedItem.containsKey("type")) {
                normalizedItem.put("type", "static");
            }
            if (target.idPrefix != null) {
                normalizedItem.put("id", String.format(Locale.ROOT, "%s-%04d", target.idPrefix, index + 1));
            }
            normalizedItem.put("created_at", createdAtBase + index);
            normalized.add(normalizedItem);
        }

        return normalized;
    }

    private static Map<String, Object> buildTargetPayload(String deviceIp, int snmpPort) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ip_address", deviceIp);
        payload.put("snmp_port", snmpPort);
        return payload;
    }

    private static String buildReplaceFilename(ReplaceTarget target) {
        return "qdns-" + target.stateField + "-replace-all.json";
    }

    private static int resolveSnmpPort(DeviceInfo device) {
        String port = device != null ? device.getDevicePort() : null;
        if (isBlank(port)) {
            return 161;
        }
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            return 161;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static RestTemplate createRestTemplate(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    private interface DeviceStateMatcher {
        boolean matched(Map<String, Object> deviceInfo);
    }

    private static class PollOutcome {
        private final boolean matched;
        private final Map<String, Object> deviceInfo;

        private PollOutcome(boolean matched, Map<String, Object> deviceInfo) {
            this.matched = matched;
            this.deviceInfo = deviceInfo;
        }
    }

    private static class ReplaceTarget {
        private final String label;
        private final String stateField;
        private final String idPrefix;
        private final String[] requiredFields;
        private final String[] allowedFields;

        private ReplaceTarget(String label,
                              String stateField,
                              String idPrefix,
                              String[] requiredFields,
                              String[] allowedFields) {
            this.label = label;
            this.stateField = stateField;
            this.idPrefix = idPrefix;
            this.requiredFields = requiredFields;
            this.allowedFields = allowedFields;
        }
    }
}
