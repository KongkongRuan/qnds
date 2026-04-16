package com.qasky.qdns.controller;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.DeviceStatus;
import com.qasky.qdns.model.SnmpResult;
import com.qasky.qdns.model.dto.DiscoverRequest;
import com.qasky.qdns.model.dto.DiscoveredHost;
import com.qasky.qdns.model.dto.PlatformDeviceSyncVO;
import com.qasky.qdns.service.DeviceCollectorService;
import com.qasky.qdns.service.RedisDeviceService;
import com.qasky.qdns.service.SnmpDiscoveryService;
import com.qasky.qdns.snmp.SnmpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 设备管理接口
 */
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceCollectorService collectorService;
    private final RedisDeviceService redisDeviceService;
    private final SnmpDiscoveryService snmpDiscoveryService;

    @Autowired
    private SnmpClient snmpClient;

    @Value("${snmp.trap.auto-set-target:true}")
    private boolean autoSetTrapTarget;

    public DeviceController(DeviceCollectorService collectorService,
                            RedisDeviceService redisDeviceService,
                            SnmpDiscoveryService snmpDiscoveryService) {
        this.collectorService = collectorService;
        this.redisDeviceService = redisDeviceService;
        this.snmpDiscoveryService = snmpDiscoveryService;
    }

    /**
     * 获取设备列表。不传 size 或 size 小于等于 0 时返回全部；否则分页：page 从 0 开始。
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDevices(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<DeviceInfo> allSorted = collectorService.getAllDevicesSorted();
        int total = allSorted.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("total", total);
        result.put("nodeKey", redisDeviceService.getNodeKey());

        if (size != null && size > 0) {
            int p = page != null && page >= 0 ? page : 0;
            int from = p * size;
            List<DeviceInfo> slice;
            if (from >= total) {
                slice = Collections.emptyList();
            } else {
                int to = Math.min(from + size, total);
                slice = new ArrayList<>(allSorted.subList(from, to));
            }
            int totalPages = total == 0 ? 0 : (total + size - 1) / size;
            result.put("page", p);
            result.put("size", size);
            result.put("totalPages", totalPages);
            result.put("data", slice);
        } else {
            result.put("data", allSorted);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 接收管控平台同步下发的设备信息
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncDeviceFromPlatform(@RequestBody PlatformDeviceSyncVO syncVO) {
        if (syncVO.getDeviceId() == null || syncVO.getDeviceIp() == null) {
            return ResponseEntity.badRequest().body(errorResult("deviceId和deviceIp不能为空"));
        }

        DeviceInfo device = new DeviceInfo();
        device.setId(syncVO.getDeviceId());
        device.setDeviceIp(syncVO.getDeviceIp());
        device.setDevicePort(String.valueOf(syncVO.getDevicePort()));
        device.setName(syncVO.getDeviceName() != null ? syncVO.getDeviceName() : "VPN-" + syncVO.getDeviceIp());
        device.setManufacturer(syncVO.getManufacturer() != null ? syncVO.getManufacturer() : "QASKY");
        device.setDeviceType(syncVO.getDeviceType() != null ? syncVO.getDeviceType() : "QVPN");
        device.setDeviceModel(syncVO.getDeviceModel() != null ? syncVO.getDeviceModel() : "默认型号");
        device.setProtocol(syncVO.getSnmpVersion());
        
        if ("SNMPv3".equalsIgnoreCase(syncVO.getSnmpVersion())) {
            device.setSnmpV3Username(syncVO.getSnmpV3Username());
            device.setSnmpV3AuthProtocol(syncVO.getSnmpV3AuthProtocol());
            device.setSnmpV3AuthPassword(syncVO.getSnmpV3AuthPassword());
            device.setSnmpV3PrivProtocol(syncVO.getSnmpV3PrivProtocol());
            device.setSnmpV3PrivPassword(syncVO.getSnmpV3PrivPassword());
        }
        device.setSshUsername(syncVO.getSshUsername());
        device.setSshPassword(syncVO.getSshPassword());
        device.setSshPort(syncVO.getSshPort());
        
        device.setCreateTime(new Date());

        // 加入本地采集队列和Redis
        collectorService.addLocalDevice(device);

        // 主动设置设备的Trap目标为本节点
        if (autoSetTrapTarget) {
            java.util.concurrent.CompletableFuture.runAsync(() -> collectorService.setTrapTarget(device));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "设备同步成功");
        result.put("trapTargetSet", autoSetTrapTarget); // 异步设置，默认返回配置状态
        result.put("data", device);
        return ResponseEntity.ok(result);
    }

    /**
     * 添加设备（本地测试，无需Redis）
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addDevice(@RequestBody DeviceInfo device) {
        if (device.getDeviceIp() == null || device.getDeviceIp().isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("deviceIp不能为空"));
        }
        if (device.getDevicePort() == null || device.getDevicePort().isEmpty()) {
            device.setDevicePort("161");
        }
        if (device.getManufacturer() == null) device.setManufacturer("QASKY");
        if (device.getDeviceType() == null) device.setDeviceType("QVPN");
        if (device.getDeviceModel() == null) device.setDeviceModel("默认型号");
        if (device.getName() == null) device.setName("VPN-" + device.getDeviceIp());
        if (device.getProtocol() == null) device.setProtocol("SNMPv3");
        device.setCreateTime(new Date());

        collectorService.addLocalDevice(device);

        if (autoSetTrapTarget) {
            java.util.concurrent.CompletableFuture.runAsync(() -> collectorService.setTrapTarget(device));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "设备添加成功");
        result.put("trapTargetSet", autoSetTrapTarget); // 异步设置，默认返回配置状态
        result.put("data", device);
        return ResponseEntity.ok(result);
    }

    /**
     * SNMP/UDP 网段发现（网络地址 + 前缀长度），返回响应 SNMP 的主机列表
     */
    @PostMapping("/discover")
    public ResponseEntity<Map<String, Object>> discover(@RequestBody DiscoverRequest request) {
        if (request.getNetworkAddress() == null || request.getNetworkAddress().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(errorResult("networkAddress不能为空"));
        }
        if (request.getPrefixLength() == null) {
            return ResponseEntity.badRequest().body(errorResult("prefixLength不能为空"));
        }
        try {
            List<DiscoveredHost> found = snmpDiscoveryService.discover(request);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("total", found.size());
            result.put("data", found);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("发现请求无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body(errorResult(e.getMessage()));
        } catch (Exception e) {
            log.error("网段发现失败", e);
            return ResponseEntity.badRequest().body(errorResult("发现失败: " + e.getMessage()));
        }
    }

    /**
     * 批量添加设备
     */
    @PostMapping("/addBatch")
    public ResponseEntity<Map<String, Object>> addBatchDevices(@RequestBody List<DeviceInfo> devices) {
        for (DeviceInfo device : devices) {
            if (device.getDeviceIp() == null) continue;
            if (device.getDevicePort() == null) device.setDevicePort("161");
            if (device.getManufacturer() == null) device.setManufacturer("QASKY");
            if (device.getDeviceType() == null) device.setDeviceType("QVPN");
            if (device.getDeviceModel() == null) device.setDeviceModel("默认型号");
            if (device.getName() == null) device.setName("VPN-" + device.getDeviceIp());
            if (device.getProtocol() == null) device.setProtocol("SNMPv3");
            device.setCreateTime(new Date());
            collectorService.addLocalDevice(device);
            
            if (autoSetTrapTarget) {
                java.util.concurrent.CompletableFuture.runAsync(() -> collectorService.setTrapTarget(device));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "批量添加成功");
        result.put("count", devices.size());
        result.put("trapTargetSetCount", autoSetTrapTarget ? devices.size() : 0); // 异步设置，返回预期数量
        return ResponseEntity.ok(result);
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Map<String, Object>> removeDevice(@PathVariable String deviceId) {
        collectorService.removeLocalDevice(deviceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "设备已移除");
        return ResponseEntity.ok(result);
    }

    /**
     * 获取设备状态（GET 参数风格，兼容 QDMS 调用）
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDeviceStatusByParam(@RequestParam Map<String, String> params) {
        String deviceId = firstNonBlank(params.get("id"), params.get("deviceId"));
        if (deviceId == null) {
            return ResponseEntity.badRequest().body(errorResult("id不能为空"));
        }
        return buildDeviceStatusResponse(deviceId);
    }

    /**
     * 获取设备状态
     */
    @GetMapping("/status/{deviceId}")
    public ResponseEntity<Map<String, Object>> getDeviceStatus(@PathVariable String deviceId) {
        return buildDeviceStatusResponse(deviceId);
    }

    private ResponseEntity<Map<String, Object>> buildDeviceStatusResponse(String deviceId) {
        DeviceStatus status = collectorService.getLatestStatus(deviceId);
        if (status == null) {
            return ResponseEntity.ok(errorResult("未找到设备状态，请先执行采集"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", status);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有设备状态
     */
    @GetMapping("/status/all")
    public ResponseEntity<Map<String, Object>> getAllDeviceStatus() {
        Map<String, DeviceStatus> statusMap = collectorService.getAllLatestStatus();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("total", statusMap.size());
        result.put("data", statusMap.values());
        return ResponseEntity.ok(result);
    }

    /**
     * 向设备下发配置/修改OID值
     */
    @PostMapping("/snmp-set")
    public ResponseEntity<Map<String, Object>> snmpSet(@RequestBody com.qasky.qdns.model.dto.SnmpSetRequest req) {
        if (req.getOid() == null || req.getValue() == null) {
            return ResponseEntity.badRequest().body(errorResult("oid和value不能为空"));
        }

        DeviceInfo device = null;
        if (req.getDeviceId() != null) {
            device = collectorService.getDeviceById(req.getDeviceId());
        } else if (req.getDeviceIp() != null) {
            String devId = collectorService.getDeviceIdByIp(req.getDeviceIp());
            if (devId != null) {
                device = collectorService.getDeviceById(devId);
            }
        }

        String targetIp = req.getDeviceIp();
        int targetPort = req.getDevicePort() != null ? req.getDevicePort() : 161;
        String protocol = "v2c";

        if (device != null) {
            if (targetIp == null) {
                targetIp = device.getDeviceIp();
            }
            if (req.getDevicePort() == null && device.getDevicePort() != null) {
                targetPort = Integer.parseInt(device.getDevicePort());
            }
            if (device.getProtocol() != null) {
                protocol = device.getProtocol();
            }
        }

        if (targetIp == null) {
            return ResponseEntity.badRequest().body(errorResult("无法确定目标设备IP，请提供deviceId或deviceIp"));
        }

        String valueType = req.getValueType() != null ? req.getValueType() : "STRING";
        SnmpResult result = snmpClient.set(targetIp, targetPort, req.getOid(), req.getValue(), valueType, protocol, device);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", result.isSuccess() ? 200 : 500);
        resp.put("message", result.isSuccess() ? "下发成功" : result.getError());
        resp.put("data", result);
        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 400);
        result.put("message", message);
        return result;
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
