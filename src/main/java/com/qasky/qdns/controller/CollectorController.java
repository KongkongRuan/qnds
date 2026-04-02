package com.qasky.qdns.controller;

import com.qasky.qdns.model.AlarmInfo;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.DeviceStatus;
import com.qasky.qdns.model.SnmpResult;
import com.qasky.qdns.service.DeviceCollectorService;
import com.qasky.qdns.service.AlarmForwardService;
import com.qasky.qdns.snmp.SnmpCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 采集控制接口
 */
@RestController
@RequestMapping("/api/collector")
public class CollectorController {

    private static final Logger log = LoggerFactory.getLogger(CollectorController.class);

    private final DeviceCollectorService collectorService;
    private final SnmpCollector snmpCollector;
    private final AlarmForwardService alarmForwardService;

    public CollectorController(DeviceCollectorService collectorService, SnmpCollector snmpCollector, AlarmForwardService alarmForwardService) {
        this.collectorService = collectorService;
        this.snmpCollector = snmpCollector;
        this.alarmForwardService = alarmForwardService;
    }

    /**
     * 手动触发全量采集
     */
    @PostMapping("/collectAll")
    public ResponseEntity<Map<String, Object>> collectAll() {
        List<DeviceStatus> results = collectorService.collectAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "采集完成");
        result.put("total", results.size());
        long online = results.stream().filter(DeviceStatus::isOnline).count();
        result.put("online", online);
        result.put("offline", results.size() - online);
        result.put("data", results);
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发单台设备采集
     */
    @PostMapping("/collect/{deviceId}")
    public ResponseEntity<Map<String, Object>> collectSingle(@PathVariable String deviceId) {
        DeviceStatus status = collectorService.collectSingle(deviceId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (status == null) {
            result.put("code", 404);
            result.put("message", "设备不存在");
        } else {
            result.put("code", 200);
            result.put("data", status);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取采集统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(collectorService.getCollectStats());
    }

    /**
     * 获取最近收到的告警列表
     */
    @GetMapping("/alarms")
    public ResponseEntity<Map<String, Object>> getRecentAlarms() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        List<AlarmInfo> alarms = alarmForwardService.getRecentAlarms();
        // 按时间倒序
        Collections.reverse(alarms);
        result.put("data", alarms);
        return ResponseEntity.ok(result);
    }

    /**
     * SNMP工具 - GET单个OID
     */
    @GetMapping("/snmpGet")
    public ResponseEntity<Map<String, Object>> snmpGet(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam String oid,
            @RequestParam(defaultValue = "SNMPv3") String protocol,
            @RequestParam(required = false) String v3Username,
            @RequestParam(required = false) String v3AuthPassword,
            @RequestParam(required = false) String v3PrivPassword,
            @RequestParam(required = false) String v3AuthProtocol,
            @RequestParam(required = false) String v3PrivProtocol,
            @RequestParam(required = false) String communityRead,
            @RequestParam(required = false) String communityWrite) {

        log.info("SNMP GET 请求参数: host={}, port={}, oid={}, protocol={}, v3Username={}, v3AuthProtocol={}, v3PrivProtocol={}, communityRead={}",
                host, port, oid, protocol, v3Username, v3AuthProtocol, v3PrivProtocol, communityRead);

        DeviceInfo v3 = v3Stub(protocol, v3Username, v3AuthPassword, v3PrivPassword, v3AuthProtocol, v3PrivProtocol);
        SnmpResult snmpResult = snmpCollector.collectSingleOid(host, port, oid, protocol, v3, communityRead);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", snmpResult.isSuccess() ? 200 : 500);
        result.put("data", snmpResult);
        return ResponseEntity.ok(result);
    }

    /**
     * SNMP工具 - 批量GET
     */
    @PostMapping("/snmpBatchGet")
    public ResponseEntity<Map<String, Object>> snmpBatchGet(@RequestBody Map<String, Object> params) {
        String host = (String) params.get("host");
        int port = params.containsKey("port") ? ((Number) params.get("port")).intValue() : 161;
        @SuppressWarnings("unchecked")
        List<String> oids = (List<String>) params.get("oids");
        String protocol = params.containsKey("protocol") ? (String) params.get("protocol") : "SNMPv3";
        String v3Username = params.containsKey("v3Username") ? (String) params.get("v3Username") : null;
        String v3AuthPassword = params.containsKey("v3AuthPassword") ? (String) params.get("v3AuthPassword") : null;
        String v3PrivPassword = params.containsKey("v3PrivPassword") ? (String) params.get("v3PrivPassword") : null;
        String v3AuthProtocol = params.containsKey("v3AuthProtocol") ? (String) params.get("v3AuthProtocol") : null;
        String v3PrivProtocol = params.containsKey("v3PrivProtocol") ? (String) params.get("v3PrivProtocol") : null;
        DeviceInfo v3 = v3Stub(protocol, v3Username, v3AuthPassword, v3PrivPassword, v3AuthProtocol, v3PrivProtocol);

        List<SnmpResult> results = new ArrayList<>();
        for (String oid : oids) {
            results.add(snmpCollector.collectSingleOid(host, port, oid, protocol, v3));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("total", results.size());
        result.put("data", results);
        return ResponseEntity.ok(result);
    }

    /**
     * SNMP工具 - WALK
     */
    @GetMapping("/snmpWalk")
    public ResponseEntity<Map<String, Object>> snmpWalk(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam String rootOid,
            @RequestParam(defaultValue = "SNMPv3") String protocol,
            @RequestParam(required = false) String v3Username,
            @RequestParam(required = false) String v3AuthPassword,
            @RequestParam(required = false) String v3PrivPassword,
            @RequestParam(required = false) String v3AuthProtocol,
            @RequestParam(required = false) String v3PrivProtocol,
            @RequestParam(required = false) String communityRead,
            @RequestParam(required = false) String communityWrite) {

        DeviceInfo v3 = v3Stub(protocol, v3Username, v3AuthPassword, v3PrivPassword, v3AuthProtocol, v3PrivProtocol);
        List<SnmpResult> walkResults = snmpCollector.walkOid(host, port, rootOid, protocol, v3, communityRead);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("total", walkResults.size());
        result.put("data", walkResults);
        return ResponseEntity.ok(result);
    }

    /**
     * SNMPv3 时若任一凭据非空则构造覆盖对象；全空则返回 null 使用全局配置
     */
    private static DeviceInfo v3Stub(String protocol, String username, String authPassword, String privPassword,
                                     String authProtocol, String privProtocol) {
        if (protocol == null || !protocol.toUpperCase().contains("V3")) {
            return null;
        }
        boolean any = (username != null && !username.isEmpty())
                || (authPassword != null && !authPassword.isEmpty())
                || (privPassword != null && !privPassword.isEmpty())
                || (authProtocol != null && !authProtocol.isEmpty())
                || (privProtocol != null && !privProtocol.isEmpty());
        if (!any) {
            return null;
        }
        DeviceInfo d = new DeviceInfo();
        d.setSnmpV3Username(username != null && !username.isEmpty() ? username : null);
        d.setSnmpV3AuthPassword(authPassword != null && !authPassword.isEmpty() ? authPassword : null);
        d.setSnmpV3PrivPassword(privPassword != null && !privPassword.isEmpty() ? privPassword : null);
        d.setSnmpV3AuthProtocol(authProtocol != null && !authProtocol.isEmpty() ? authProtocol : null);
        d.setSnmpV3PrivProtocol(privProtocol != null && !privProtocol.isEmpty() ? privProtocol : null);
        log.info("v3Stub 创建 DeviceInfo: username={}, authProtocol={}, privProtocol={}",
                d.getSnmpV3Username(), d.getSnmpV3AuthProtocol(), d.getSnmpV3PrivProtocol());
        return d;
    }
}
