package com.qasky.qdns.model.dto;

import lombok.Data;

/**
 * SNMP UDP 网段发现请求（网络地址 + 前缀长度）
 */
@Data
public class DiscoverRequest {

    /** 网络地址，如 192.168.5.0 */
    private String networkAddress;

    /** 前缀长度，如 24 */
    private Integer prefixLength;

    /** SNMP UDP 端口，默认 161 */
    private Integer snmpPort = 161;

    /** SNMPv2c 或 SNMPv3 */
    private String protocol = "SNMPv2c";

    /** SNMPv2c 读 community，空则使用配置 snmp.v2c.community-read */
    private String community;

    /** SNMPv3 用户名，空则使用全局配置 */
    private String v3Username;
    /** SNMPv3 认证密码，空则使用全局配置 */
    private String v3AuthPassword;
    /** SNMPv3 加密密码，空则使用全局配置 */
    private String v3PrivPassword;
    /** SNMPv3 认证协议(SHA/MD5)，空则使用全局配置 */
    private String v3AuthProtocol;
    /** SNMPv3 加密协议(AES128/DES)，空则使用全局配置 */
    private String v3PrivProtocol;

    /** 单主机探测超时（毫秒） */
    private Integer timeoutMs = 500;

    /** SNMP 重试次数（发现时建议 0） */
    private Integer retries = 0;

    /** 并发探测线程数 */
    private Integer maxConcurrent = 32;
}
