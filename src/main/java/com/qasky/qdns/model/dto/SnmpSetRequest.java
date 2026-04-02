package com.qasky.qdns.model.dto;

import lombok.Data;

/**
 * 接收 SNMP SET 外部下发请求参数
 */
@Data
public class SnmpSetRequest {
    private String deviceId;     // 设备ID（可选，有的话可自动查出IP/端口/协议）
    private String deviceIp;     // 设备IP
    private Integer devicePort;  // 设备端口
    private String oid;          // 需要修改的OID
    private String value;        // 需要修改的值
    private String valueType;    // 值类型: STRING, INTEGER, OID, IPADDRESS, COUNTER, GAUGE, TIMETICKS
}
