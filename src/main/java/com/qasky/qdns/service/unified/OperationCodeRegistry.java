package com.qasky.qdns.service.unified;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 统一运维编码注册表
 */
@Component
public class OperationCodeRegistry {

    private final Map<String, OperationDefinition> definitions = new LinkedHashMap<>();
    private final Map<Integer, List<String>> operationsByCode = new LinkedHashMap<>();

    public OperationCodeRegistry() {
        register(new OperationDefinition(10000, "专线配置", "set_base_nego", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:set_base_nego_info",
                required("interface_name", "local_port", "local_port_nat", "ike_ver", "auth_type")));
        register(new OperationDefinition(10000, "专线配置", "get_base_nego", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_base_nego_info",
                required()));
        register(new OperationDefinition(10000, "专线配置", "set_anonymous_nego", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:set_anonymous_nego_info",
                required("status", "ph1_algs", "ph2_algs", "ph1_ttl_range", "ph2_ttl_range", "encap_protocol", "encap_mode")));
        register(new OperationDefinition(10000, "专线配置", "get_anonymous_nego", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_anonymous_nego_info",
                required()));
        register(new OperationDefinition(10000, "专线配置", "add_policy", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:add_policy_info",
                required("name", "status", "tu_name", "src_addr", "dst_addr", "action", "protocol")));
        register(new OperationDefinition(10000, "专线配置", "update_policy", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:change_policy_info",
                required("name", "id", "status", "tu_name", "src_addr", "dst_addr", "action", "protocol")));
        register(new OperationDefinition(10000, "专线配置", "get_policy_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_policy_list",
                required("page", "rows")));
        register(new OperationDefinition(10000, "专线配置", "get_policy_info", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_policy_info",
                required("name")));
        register(new OperationDefinition(10000, "专线配置", "get_policy_state_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_policy_state_list",
                required()));
        register(new OperationDefinition(10000, "专线配置", "add_tunnel", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:add_tun_info",
                required("name", "local_addr_type", "local_addr", "remote_addr_type", "remote_addr",
                        "ph1_algs", "ph2_algs", "encap_mode", "ph1_ttl", "ph2_ttl", "encap_protocol", "dpd_state", "dpd_interval")));
        register(new OperationDefinition(10000, "专线配置", "update_tunnel", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:change_tun_info",
                required("name", "id", "local_addr_type", "local_addr", "remote_addr_type", "remote_addr",
                        "ph1_algs", "ph2_algs", "encap_mode", "ph1_ttl", "ph2_ttl", "encap_protocol", "dpd_state", "dpd_interval")));
        register(new OperationDefinition(10000, "专线配置", "get_tunnel_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_tun_list",
                required("page", "rows")));
        register(new OperationDefinition(10000, "专线配置", "get_tunnel_info", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:get_tun_info",
                required("name")));

        register(new OperationDefinition(10001, "ACL配置", "add", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:add_acl",
                required("name", "action", "protocol", "src_addr", "dst_addr", "time_limit_state", "begin_time", "end_time")));
        register(new OperationDefinition(10001, "ACL配置", "delete", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:del_acl",
                required("id")));
        register(new OperationDefinition(10001, "ACL配置", "get_acl_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_acl_list",
                required("page", "rows")));

        register(new OperationDefinition(10002, "路由配置", "add", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:add_route_info",
                required("dst_addr", "mask", "next_ip", "interface_name", "weight", "distance")));
        register(new OperationDefinition(10002, "路由配置", "delete", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:del_route_info",
                required("dst_addr", "mask", "next_ip", "interface_name")));
        register(new OperationDefinition(10002, "路由配置", "get_route_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_route_list",
                required("page", "rows")));

        register(new OperationDefinition(10003, "白名单配置", "add", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:add_whitelists",
                required("name", "type", "addr", "state")));
        register(new OperationDefinition(10003, "白名单配置", "delete", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:del_whitelists",
                required("id")));
        register(new OperationDefinition(10003, "白名单配置", "update_state", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:change_whitelists",
                required("id", "state")));
        register(new OperationDefinition(10003, "白名单配置", "get_whitelist_state", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_whitelists_state",
                required()));
        register(new OperationDefinition(10003, "白名单配置", "get_whitelist_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_whitelists",
                required("page", "rows")));

        register(new OperationDefinition(10004, "NAT配置", "add_snat", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:add_snat",
                required("name", "src_addr", "dst_addr")));
        register(new OperationDefinition(10004, "NAT配置", "delete_snat", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:del_snat",
                required("id")));
        register(new OperationDefinition(10004, "NAT配置", "get_snat_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_snat_list",
                required("page", "rows")));
        register(new OperationDefinition(10004, "NAT配置", "add_dnat", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:add_dnat",
                required("name", "protocol", "local_addr", "local_port", "external_addr", "external_port")));
        register(new OperationDefinition(10004, "NAT配置", "delete_dnat", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:del_dnat",
                required("id")));
        register(new OperationDefinition(10004, "NAT配置", "get_dnat_list", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2net_agent:get_dnat_list",
                required("page", "rows")));

        register(new OperationDefinition(10005, "备份", "backup", "vpn-sim-http",
                "SYNC", "backup", required()));
        register(new OperationDefinition(10006, "恢复", "restore", "vpn-sim-http",
                "SYNC", "restore", required("zipBase64")));
        register(new OperationDefinition(10007, "升级", "upgrade", "vpn-sim-http",
                "ASYNC", "upgrade", required("version")));
        register(new OperationDefinition(10008, "重启", "reboot", "vpn-sim-http",
                "ASYNC", "reboot", required()));

        register(new OperationDefinition(10009, "下发CA证书链", "upload_ca_chain", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:upload_ca_chain", required()));
        register(new OperationDefinition(10009, "下发CA证书链", "upload_root_cert", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:upload_ca_chain", required()));
        register(new OperationDefinition(10010, "下发设备证书", "upload_cert", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:upload_cert", required()));
        register(new OperationDefinition(10011, "查询证书状态", "query_cert_status", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:query_cert_status", required()));
        register(new OperationDefinition(10012, "读取设备证书", "read_device_cert", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:read_device_cert", required()));
        register(new OperationDefinition(10013, "读取CA证书链", "read_ca_chain", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:read_ca_chain", required()));
        register(new OperationDefinition(10014, "读取设备CSR", "read_device_csr", "ssl-vpn-ipc",
                "SYNC", "ipc:req:web_agent2ipsec_agent:read_device_csr", required()));
    }

    public OperationDefinition getDefinition(Integer code, String operation) {
        if (code == null) {
            throw new IllegalArgumentException("code不能为空");
        }
        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("operation不能为空");
        }

        String normalizedOperation = operation.trim().toLowerCase(Locale.ROOT);
        OperationDefinition definition = definitions.get(buildKey(code, normalizedOperation));
        if (definition != null) {
            return definition;
        }

        List<String> supportedOperations = operationsByCode.get(code);
        if (supportedOperations == null || supportedOperations.isEmpty()) {
            throw new IllegalArgumentException("不支持的code: " + code);
        }
        throw new IllegalArgumentException("code " + code + " 不支持operation=" + normalizedOperation
                + "，支持的operation: " + supportedOperations);
    }

    public void validatePayload(OperationDefinition definition, Map<String, Object> payload) {
        Set<String> missingFields = new LinkedHashSet<>();
        Map<String, Object> safePayload = payload != null ? payload : Collections.<String, Object>emptyMap();
        for (String field : definition.getRequiredFields()) {
            Object value = safePayload.get(field);
            if (value == null) {
                missingFields.add(field);
            } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                missingFields.add(field);
            }
        }
        if (!missingFields.isEmpty()) {
            throw new IllegalArgumentException("payload缺少必填字段: " + String.join(", ", missingFields));
        }
    }

    private void register(OperationDefinition definition) {
        definitions.put(buildKey(definition.getCode(), definition.getOperation()), definition);
        operationsByCode.computeIfAbsent(definition.getCode(), key -> new ArrayList<String>())
                .add(definition.getOperation());
    }

    private static String buildKey(Integer code, String operation) {
        return code + "#" + operation;
    }

    private static Set<String> required(String... fields) {
        LinkedHashSet<String> requiredFields = new LinkedHashSet<>();
        if (fields != null) {
            requiredFields.addAll(Arrays.asList(fields));
        }
        return Collections.unmodifiableSet(requiredFields);
    }

    public static class OperationDefinition {
        private final int code;
        private final String capabilityName;
        private final String operation;
        private final String executorKey;
        private final String mode;
        private final String downstreamAction;
        private final Set<String> requiredFields;

        public OperationDefinition(int code,
                                   String capabilityName,
                                   String operation,
                                   String executorKey,
                                   String mode,
                                   String downstreamAction,
                                   Set<String> requiredFields) {
            this.code = code;
            this.capabilityName = capabilityName;
            this.operation = operation;
            this.executorKey = executorKey;
            this.mode = mode;
            this.downstreamAction = downstreamAction;
            this.requiredFields = requiredFields;
        }

        public int getCode() {
            return code;
        }

        public String getCapabilityName() {
            return capabilityName;
        }

        public String getOperation() {
            return operation;
        }

        public String getExecutorKey() {
            return executorKey;
        }

        public String getMode() {
            return mode;
        }

        public String getDownstreamAction() {
            return downstreamAction;
        }

        public Set<String> getRequiredFields() {
            return requiredFields;
        }
    }
}
