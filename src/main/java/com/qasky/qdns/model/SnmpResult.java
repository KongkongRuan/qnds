package com.qasky.qdns.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个SNMP查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SnmpResult {

    private String oid;
    private String value;
    private String type;
    private String error;

    public boolean isSuccess() {
        return error == null || error.isEmpty();
    }

    public static SnmpResult success(String oid, String value, String type) {
        return new SnmpResult(oid, value, type, null);
    }

    public static SnmpResult error(String oid, String errorMsg) {
        return new SnmpResult(oid, null, null, errorMsg);
    }
}
