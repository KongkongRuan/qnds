package com.qasky.qdns.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 设备基础信息（对应管理平台分配到Redis中的设备数据）
 */
@Data
public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String manufacturer;
    private String deviceType;
    private String deviceModel;
    private String name;
    private String deviceIp;
    private String devicePort;
    private String protocol;
    /** SNMPv3 用户名，空则使用全局 snmp.v3.username */
    private String snmpV3Username;
    /** SNMPv3 认证密码，空则使用全局 snmp.v3.auth-password */
    private String snmpV3AuthPassword;
    /** SNMPv3 加密密码，空则使用全局 snmp.v3.priv-password */
    private String snmpV3PrivPassword;
    /** SNMPv3 认证协议(SHA/MD5)，空则使用全局 snmp.v3.auth-protocol */
    private String snmpV3AuthProtocol;
    /** SNMPv3 加密协议(AES128/DES)，空则使用全局 snmp.v3.priv-protocol */
    private String snmpV3PrivProtocol;
    /** 设置Trap目标使用的SNMP协议，空则跟随 protocol */
    private String trapProtocol;
    /** 设置Trap目标使用的v2c写community，空则使用全局 snmp.v2c.community-write */
    private String trapCommunityWrite;
    private String version;
    private String status;
    private String cpuUsed;
    private String memUsed;
    private String diskUsed;
    private String passwordCardUseCount;
    private String algorithmIdentification;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date updateTime;
}
