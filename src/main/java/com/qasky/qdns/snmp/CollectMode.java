package com.qasky.qdns.snmp;

/**
 * SNMP 采集模式：全量（含接口表、VPN 表等）与高频指标（仅标量、无 walk）。
 */
public enum CollectMode {
    /** 全量：标量 + ifTable/ifXTable + VPN 隧道/IKE/IPsec 表 */
    FULL,
    /** 高频：仅 CPU/内存/磁盘/密码卡调用与算法等标量，不做表遍历 */
    METRICS_ONLY
}
