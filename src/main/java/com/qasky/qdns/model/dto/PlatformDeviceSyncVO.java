package com.qasky.qdns.model.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 管控平台向服务节点同步设备信息的VO对象
 */
@Data
public class PlatformDeviceSyncVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * QDMS 管控平台分配的全局唯一设备ID。
     * QDNS 对外以该字段作为北向设备标识，不要求与 VPN-Sim 的 device_id 一致。
     */
    private String deviceId;

    /**
     * 设备IP地址。QDNS 会结合 devicePort 按设备地址定位下游设备。
     */
    private String deviceIp;

    /**
     * 设备SNMP端口，默认161。QDNS 会结合 deviceIp 按设备地址定位下游设备。
     */
    private Integer devicePort = 161;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备厂商
     */
    private String manufacturer;

    /**
     * 设备类型 (如 QVPN, QSW 等)
     */
    private String deviceType;

    /**
     * 设备型号
     */
    private String deviceModel;

    /**
     * SNMP协议版本: SNMPv2c 或 SNMPv3
     */
    private String snmpVersion = "SNMPv3";

    /**
     * SNMPv2c 读团体名
     */
    private String snmpV2cReadCommunity;

    /**
     * SNMPv2c 写团体名 (用于设置Trap目标)
     */
    private String snmpV2cWriteCommunity;

    /**
     * SNMPv3 用户名
     */
    private String snmpV3Username;

    /**
     * SNMPv3 认证密码
     */
    private String snmpV3AuthPassword;

    /**
     * SNMPv3 加密密码
     */
    private String snmpV3PrivPassword;

    /**
     * SNMPv3 认证协议 (SHA/MD5/HMAC128SHA224/HMAC192SHA256/SM3)
     */
    private String snmpV3AuthProtocol;

    /**
     * SNMPv3 加密协议 (AES128/AES192/AES256/DES/SM4)
     */
    private String snmpV3PrivProtocol;

    /**
     * 运维账号 (可选，用于SSH登录下发配置)
     */
    private String sshUsername;

    /**
     * 运维密码 (可选)
     */
    private String sshPassword;

    /**
     * SSH端口 (可选，默认22)
     */
    private Integer sshPort = 22;
}
