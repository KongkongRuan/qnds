package com.qasky.qdns.controller;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.SnmpResult;
import com.qasky.qdns.model.dto.ConfigRequest;
import com.qasky.qdns.model.dto.ConfigResponse;
import com.qasky.qdns.service.DeviceCollectorService;
import com.qasky.qdns.snmp.SnmpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置下发接口
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private SnmpClient snmpClient;

    @Autowired
    private DeviceCollectorService deviceCollectorService;

    /**
     * 配置下发接口
     */
    @PostMapping("/set")
    public ResponseEntity<ConfigResponse> setConfig(@RequestBody ConfigRequest request) {
        ConfigResponse response = new ConfigResponse();
        response.setDeviceId(request.getDeviceId());

        DeviceInfo device = deviceCollectorService.getDeviceById(request.getDeviceId());
        if (device == null) {
            response.setCode(404);
            response.setMessage("设备不存在");
            return ResponseEntity.status(404).body(response);
        }

        List<ConfigResponse.ConfigResult> results = new ArrayList<>();
        int successCount = 0;

        for (ConfigRequest.ConfigItem item : request.getConfigs()) {
            ConfigResponse.ConfigResult result = new ConfigResponse.ConfigResult();
            result.setOid(item.getOid());
            result.setNewValue(item.getValue());

            try {
                SnmpResult snmpResult = snmpClient.set(
                        device.getDeviceIp(),
                        Integer.parseInt(device.getDevicePort()),
                        item.getOid(),
                        item.getValue(),
                        item.getType(),
                        device.getProtocol(),
                        device
                );

                result.setSuccess(snmpResult.isSuccess());
                result.setMessage(snmpResult.getError());

                if (snmpResult.isSuccess()) {
                    successCount++;
                    log.info("配置下发成功: 设备={}, OID={}, 值={}",
                            device.getDeviceIp(), item.getOid(), item.getValue());
                } else {
                    log.warn("配置下发失败: 设备={}, OID={}, 错误={}",
                            device.getDeviceIp(), item.getOid(), snmpResult.getError());
                }
            } catch (Exception e) {
                result.setSuccess(false);
                result.setMessage(e.getMessage());
                log.error("配置下发异常: 设备={}, OID={}", device.getDeviceIp(), item.getOid(), e);
            }

            results.add(result);
        }

        response.setCode(200);
        response.setMessage("配置下发完成");
        response.setTotalCount(request.getConfigs().size());
        response.setSuccessCount(successCount);
        response.setFailCount(request.getConfigs().size() - successCount);
        response.setResults(results);

        return ResponseEntity.ok(response);
    }

    /**
     * 批量配置下发（多设备）
     */
    @PostMapping("/batch-set")
    public ResponseEntity<Map<String, Object>> batchSetConfig(
            @RequestBody List<ConfigRequest> requests) {

        Map<String, Object> result = new LinkedHashMap<>();
        int totalSuccess = 0;
        int totalFail = 0;
        List<String> failedDevices = new ArrayList<>();

        for (ConfigRequest request : requests) {
            DeviceInfo device = deviceCollectorService.getDeviceById(request.getDeviceId());
            if (device == null) {
                totalFail += request.getConfigs().size();
                failedDevices.add(request.getDeviceId() + "(设备不存在)");
                continue;
            }

            for (ConfigRequest.ConfigItem item : request.getConfigs()) {
                try {
                    SnmpResult snmpResult = snmpClient.set(
                            device.getDeviceIp(),
                            Integer.parseInt(device.getDevicePort()),
                            item.getOid(),
                            item.getValue(),
                            item.getType(),
                            device.getProtocol(),
                            device
                    );

                    if (snmpResult.isSuccess()) {
                        totalSuccess++;
                    } else {
                        totalFail++;
                    }
                } catch (Exception e) {
                    totalFail++;
                }
            }
        }

        result.put("code", 200);
        result.put("message", "批量配置下发完成");
        result.put("totalSuccess", totalSuccess);
        result.put("totalFail", totalFail);
        result.put("failedDevices", failedDevices);

        return ResponseEntity.ok(result);
    }

    /**
     * 设置设备Trap目标地址
     */
    @PostMapping("/set-trap-target/{deviceId}")
    public ResponseEntity<Map<String, Object>> setTrapTarget(
            @PathVariable String deviceId,
            @RequestParam(required = false) String targetIp,
            @RequestParam(defaultValue = "162") int targetPort) {

        Map<String, Object> result = new LinkedHashMap<>();

        DeviceInfo device = deviceCollectorService.getDeviceById(deviceId);
        if (device == null) {
            result.put("code", 404);
            result.put("message", "设备不存在");
            return ResponseEntity.status(404).body(result);
        }

        String trapTargetIp = targetIp;
        if (trapTargetIp == null || trapTargetIp.isEmpty()) {
            try {
                trapTargetIp = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                trapTargetIp = "127.0.0.1";
            }
        }

        String trapTargetOid = "1.3.6.1.6.3.12.1.2.1.4.1";
        String trapTargetPortOid = "1.3.6.1.6.3.12.1.2.1.5.1";

        try {
            SnmpResult ipResult = snmpClient.set(
                    device.getDeviceIp(),
                    Integer.parseInt(device.getDevicePort()),
                    trapTargetOid,
                    trapTargetIp,
                    "STRING",
                    device.getProtocol(),
                    device
            );

            SnmpResult portResult = snmpClient.set(
                    device.getDeviceIp(),
                    Integer.parseInt(device.getDevicePort()),
                    trapTargetPortOid,
                    String.valueOf(targetPort),
                    "INTEGER",
                    device.getProtocol(),
                    device
            );

            if (ipResult.isSuccess() && portResult.isSuccess()) {
                result.put("code", 200);
                result.put("message", "Trap目标设置成功");
                result.put("trapTargetIp", trapTargetIp);
                result.put("trapTargetPort", targetPort);
                log.info("设备Trap目标设置成功: deviceId={}, target={}:{}",
                        deviceId, trapTargetIp, targetPort);
            } else {
                result.put("code", 500);
                result.put("message", "Trap目标设置失败");
                result.put("ipResult", ipResult.getError());
                result.put("portResult", portResult.getError());
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Trap目标设置异常: " + e.getMessage());
            log.error("设备Trap目标设置异常: deviceId={}", deviceId, e);
        }

        return ResponseEntity.ok(result);
    }
}
