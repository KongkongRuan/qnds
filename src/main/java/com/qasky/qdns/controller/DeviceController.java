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
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
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

    @Value("${snmp.trap.target-port:1162}")
    private int trapTargetPort;

    private final RestTemplate restTemplate = new RestTemplate();

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
        
        device.setCreateTime(new Date());

        // 加入本地采集队列和Redis
        collectorService.addLocalDevice(device);

        // 主动设置设备的Trap目标为本节点
        if (autoSetTrapTarget) {
            java.util.concurrent.CompletableFuture.runAsync(() -> setTrapTarget(device));
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
            java.util.concurrent.CompletableFuture.runAsync(() -> setTrapTarget(device));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "设备添加成功");
        result.put("trapTargetSet", autoSetTrapTarget); // 异步设置，默认返回配置状态
        result.put("data", device);
        return ResponseEntity.ok(result);
    }

    /**
     * 设置设备的Trap目标地址为当前节点IP。
     * 优先使用 device.trapProtocol，为空则跟随 device.protocol。
     */
    private boolean setTrapTarget(DeviceInfo device) {
        try {
            String nodeIp = InetAddress.getLocalHost().getHostAddress();

            // 确定设置 Trap 使用的 SNMP 协议：优先 trapProtocol，回退到 protocol
            String effectiveProtocol = (device.getTrapProtocol() != null && !device.getTrapProtocol().trim().isEmpty())
                    ? device.getTrapProtocol().trim()
                    : device.getProtocol();
            String communityOverride = device.getTrapCommunityWrite();

            // 1. 尝试使用标准的SNMP SET来修改设备的Trap目标 (RFC 3413 SNMP-TARGET-MIB)
            String trapTargetOid = "1.3.6.1.6.3.12.1.2.1.4.1";
            String trapTargetPortOid = "1.3.6.1.6.3.12.1.2.1.5.1";

            log.info("尝试通过SNMP SET设置Trap目标, deviceIp={}, target={}:{}, protocol={}", device.getDeviceIp(), nodeIp, trapTargetPort, effectiveProtocol);

            SnmpResult ipResult = snmpClient.set(
                    device.getDeviceIp(),
                    Integer.parseInt(device.getDevicePort()),
                    trapTargetOid,
                    nodeIp,
                    "STRING",
                    effectiveProtocol,
                    device,
                    communityOverride
            );

            if (ipResult.isSuccess()) {
                snmpClient.set(device.getDeviceIp(), Integer.parseInt(device.getDevicePort()), trapTargetPortOid, String.valueOf(trapTargetPort), "INTEGER", effectiveProtocol, device, communityOverride);
                log.info("设备Trap目标SNMP设置成功: deviceId={}, target={}:{}", device.getId(), nodeIp, trapTargetPort);
                return true;
            } else {
                log.warn("设备Trap目标SNMP设置失败 (可能是模拟器或设备不支持该MIB): deviceId={}, error={}", device.getId(), ipResult.getError());

                // 2. 如果是模拟环境(VPN-Sim)，尝试通过模拟器的API强制设置Trap目标
                return trySetVpnSimTrapTarget(nodeIp);
            }
        } catch (Exception e) {
            log.error("设置Trap目标异常: deviceId={}", device.getId(), e);
            return false;
        }
    }

    /**
     * 针对VPN-Sim模拟器的特殊处理，通过调用其API设置Trap目标
     */
    private boolean trySetVpnSimTrapTarget(String nodeIp) {
        try {
            // 假设VPN-Sim控制API在本地8888端口
            String vpnSimApiUrl = "http://127.0.0.1:8888/api/config";
            Map<String, Object> req = new HashMap<>();
            Map<String, Object> trapConfig = new HashMap<>();
            List<Map<String, Object>> targets = new ArrayList<>();
            Map<String, Object> target = new HashMap<>();
            target.put("host", nodeIp);
            target.put("port", trapTargetPort);
            target.put("community", "public");
            targets.add(target);
            trapConfig.put("targets", targets);
            req.put("trap", trapConfig);

            // VPN-Sim 使用 PUT 请求更新配置
            restTemplate.put(vpnSimApiUrl, req);
            log.info("已通过VPN-Sim API成功注入Trap目标: {}:{}", nodeIp, trapTargetPort);
            return true;
        } catch (Exception e) {
            log.debug("尝试调用VPN-Sim API设置Trap失败 (忽略): {}", e.getMessage());
            return false;
        }
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
                java.util.concurrent.CompletableFuture.runAsync(() -> setTrapTarget(device));
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
     * 获取设备状态
     */
    @GetMapping("/status/{deviceId}")
    public ResponseEntity<Map<String, Object>> getDeviceStatus(@PathVariable String deviceId) {
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
}
