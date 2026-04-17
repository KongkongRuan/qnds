package com.qasky.qdns.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 设备采集到的完整状态数据
 */
@Data
public class DeviceStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String deviceIp;
    private int snmpPort;
    private boolean online;
    private String errorMessage;

    // 系统信息
    private String sysDescr;
    private String sysObjectID;
    private long sysUpTime;
    private String sysContact;
    private String sysName;
    private String sysLocation;

    // 资源使用
    private int cpuUsage;
    private int cpuIdle;
    private long memTotalKb;
    private long memAvailKb;
    private int memUsagePercent;
    private int diskPercent;

    // 私有MIB - 设备信息
    private int vpnDeviceType;
    private String vpnFirmwareVersion;
    private String vpnSerialNumber;
    private int vpnDeviceStatus;
    private String vpnMacAddress;
    private String vpnDeviceModel;
    private String vpnVendor;

    // 隧道
    private int tunnelCount;
    private List<TunnelStatus> tunnels;

    // 接口
    private int ifNumber;
    private List<InterfaceStatus> interfaces;

    // 无线
    private int wirelessStatus;
    private long wirelessInOctets;
    private long wirelessOutOctets;

    // 密码卡
    private int cryptoCardStatus;
    private long cryptoCardCallCount;
    private String cryptoCardAlgorithms;
    private String cryptoCardModel;
    private long cryptoCardErrorCount;
    private int cryptoCardCompliance;

    // IKE SA
    private int ikeCount;
    private List<IkeSaStatus> ikeSas;

    // IPsec SA
    private List<IpsecSaStatus> ipsecSas;

    // 防火墙与路由统计
    private int firewallRuleCount;
    private int firewallAclCount;
    private int firewallSnatCount;
    private int firewallDnatCount;
    private int firewallWhitelistCount;
    private int routeIpv4Count;
    private int routeIpv6Count;
    private int routeTotalCount;

    // 全部OID原始数据
    private Map<String, String> rawOidData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date collectTime;

    @Data
    public static class InterfaceStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private int index;
        private String name;
        private String macAddress;
        private String ipAddress;
        private int ifType;
        private long speed;
        private int adminStatus;
        private int operStatus;
        private long inOctets;
        private long outOctets;
        private long hcInOctets;
        private long hcOutOctets;
        private long highSpeed;
    }

    @Data
    public static class TunnelStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private int index;
        private String name;
        private int status;
        private long inOctets;
        private long outOctets;
        private String localAddr;
        private String remoteAddr;
        private String ikeRuleName;
        private String encryptAlgo;
        private int keySource;
        private long currentRateBps;
        private long throughputBytes;
    }

    @Data
    public static class IkeSaStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private int index;
        private String localAddr;
        private String remoteAddr;
        private int status;
        private int version;
        private int authMethod;
        private String encryptAlgo;
        private String hashAlgo;
        private String dhGroup;
        private int rekeyRemain;
        private int dpdEnabled;
    }

    @Data
    public static class IpsecSaStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private int index;
        private int tunnelId;
        private int protocol;
        private String encryptAlgo;
        private String authAlgo;
        private int keySource;
        private int rekeyRemain;
        private int workMode;
        private long establishTime;
        private long inBytes;
        private long outBytes;
    }
}
