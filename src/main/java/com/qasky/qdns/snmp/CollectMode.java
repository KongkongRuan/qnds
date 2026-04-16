package com.qasky.qdns.snmp;

/**
 * SNMP 采集模式：全量（含接口表、VPN 表等）与高频指标。
 */
public enum CollectMode {
    /** 全量：标量 + ifTable/ifXTable + VPN 隧道/IKE/IPsec 表 */
    FULL,
    /** 高频：CPU/内存/磁盘/密码卡标量 + 隧道表（用于速率/吞吐量等动态指标） */
    METRICS_ONLY
}
