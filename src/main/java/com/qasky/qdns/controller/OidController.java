package com.qasky.qdns.controller;

import com.qasky.qdns.config.OidRegistry;
import com.qasky.qdns.model.OidDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * OID管理接口
 */
@RestController
@RequestMapping("/api/oid")
public class OidController {

    private final OidRegistry oidRegistry;

    public OidController(OidRegistry oidRegistry) {
        this.oidRegistry = oidRegistry;
    }

    /**
     * 获取指定设备类型的标量OID列表
     */
    @GetMapping("/scalar")
    public ResponseEntity<Map<String, Object>> getScalarOids(
            @RequestParam(defaultValue = "quantum_vpn") String deviceType) {
        List<OidDefinition> oids = oidRegistry.getScalarOids(deviceType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("deviceType", deviceType);
        result.put("total", oids.size());
        result.put("data", oids);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取完整OID列表（标量+表）
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllOids(
            @RequestParam(defaultValue = "QASKY") String manufacturer,
            @RequestParam(defaultValue = "QVPN") String deviceType,
            @RequestParam(defaultValue = "默认型号") String deviceModel) {

        String mappedType = OidRegistry.mapDeviceType(deviceType);
        List<OidDefinition> oids = oidRegistry.getOidList(manufacturer, mappedType, deviceModel);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("manufacturer", manufacturer);
        result.put("deviceType", deviceType);
        result.put("mappedType", mappedType);
        result.put("deviceModel", deviceModel);
        result.put("total", oids.size());
        result.put("data", oids);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取表列定义
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> getTableDefinitions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("ifTable", oidRegistry.getIfTableColumns());
        result.put("ifxTable", oidRegistry.getIfxTableColumns());
        result.put("ipAddrTable", oidRegistry.getIpAddrTableColumns());
        result.put("tunnelTable", oidRegistry.getTunnelTableColumns());
        result.put("ikeSaTable", oidRegistry.getIkeSaTableColumns());
        result.put("ipsecSaTable", oidRegistry.getIpsecSaTableColumns());
        return ResponseEntity.ok(result);
    }
}
