package com.qasky.qdns.config;

import com.qasky.qdns.model.OidDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * OID注册表 - 维护不同厂家/类型/型号对应的OID列表
 * 与VPN-Sim中的oid_tree.py完全对应
 */
@Component
public class OidRegistry {

    private static final String EP = "1.3.6.1.4.1.99999";

    /** 高频指标采集使用的标量名称（与 {@link #addScalar} 中 name 一致） */
    private static final Set<String> METRICS_SCALAR_NAMES = new HashSet<>(Arrays.asList(
            "ssCpuUser", "ssCpuIdle",
            "memTotalReal", "memAvailReal", "dskPercent",
            "cryptoCardCallCount", "cryptoCardAlgorithms"
    ));

    private final Map<String, List<OidDefinition>> oidCache = new HashMap<>();
    private List<OidDefinition> allScalarOids;
    private List<OidDefinition> ifTableColumns;
    private List<OidDefinition> ifxTableColumns;
    private List<OidDefinition> tunnelTableColumns;
    private List<OidDefinition> ikeSaTableColumns;
    private List<OidDefinition> ipsecSaTableColumns;

    @PostConstruct
    public void init() {
        initScalarOids();
        initTableColumns();
    }

    private void initScalarOids() {
        allScalarOids = new ArrayList<>();
        // System MIB
        addScalar("1.3.6.1.2.1.1.1.0", "sysDescr", "OctetString", "系统描述", null);
        addScalar("1.3.6.1.2.1.1.2.0", "sysObjectID", "ObjectIdentifier", "系统对象标识", null);
        addScalar("1.3.6.1.2.1.1.3.0", "sysUpTime", "TimeTicks", "系统累计运行时间", null);
        addScalar("1.3.6.1.2.1.1.4.0", "sysContact", "OctetString", "系统联系信息", null);
        addScalar("1.3.6.1.2.1.1.5.0", "sysName", "OctetString", "系统名称", null);
        addScalar("1.3.6.1.2.1.1.6.0", "sysLocation", "OctetString", "系统位置", null);
        // ifNumber
        addScalar("1.3.6.1.2.1.2.1.0", "ifNumber", "Integer32", "网络接口数量", null);
        // UCD-SNMP (CPU/Memory/Disk)
        addScalar("1.3.6.1.4.1.2021.4.5.0", "memTotalReal", "Integer32", "物理内存总量(KB)", null);
        addScalar("1.3.6.1.4.1.2021.4.6.0", "memAvailReal", "Integer32", "可用物理内存(KB)", null);
        addScalar("1.3.6.1.4.1.2021.9.1.9.1", "dskPercent", "Integer32", "磁盘使用百分比", null);
        addScalar("1.3.6.1.4.1.2021.11.9.0", "ssCpuUser", "Integer32", "CPU用户态占用(%)", null);
        addScalar("1.3.6.1.4.1.2021.11.11.0", "ssCpuIdle", "Integer32", "CPU空闲(%)", null);

        // 私有MIB: 设备信息
        addScalar(EP + ".1.1.0", "vpnDeviceType", "Integer32", "设备类型", null);
        addScalar(EP + ".1.2.0", "vpnFirmwareVersion", "OctetString", "固件版本", null);
        addScalar(EP + ".1.3.0", "vpnSerialNumber", "OctetString", "序列号", null);
        addScalar(EP + ".1.4.0", "vpnDeviceStatus", "Integer32", "设备运行状态", null);
        addScalar(EP + ".1.5.0", "vpnMacAddress", "OctetString", "MAC地址", null);
        addScalar(EP + ".1.6.0", "vpnDeviceModel", "OctetString", "设备型号", null);
        addScalar(EP + ".1.7.0", "vpnVendor", "OctetString", "厂商名称", null);

        // 隧道总数
        Set<String> qvTp = new HashSet<>(Arrays.asList("quantum_vpn", "third_party_vpn"));
        addScalar(EP + ".2.1.0.0", "tunnelCount", "Integer32", "隧道数量", qvTp);

        // 无线
        Set<String> qvOnly = new HashSet<>(Collections.singletonList("quantum_vpn"));
        addScalar(EP + ".3.1.0", "wirelessStatus", "Integer32", "无线模块状态", qvOnly);
        addScalar(EP + ".3.2.0", "wirelessInOctets", "Counter64", "无线入方向字节计数", qvOnly);
        addScalar(EP + ".3.3.0", "wirelessOutOctets", "Counter64", "无线出方向字节计数", qvOnly);

        // 密码卡
        Set<String> qvQe = new HashSet<>(Arrays.asList("quantum_vpn", "quantum_encryptor"));
        addScalar(EP + ".4.1.0", "cryptoCardStatus", "Integer32", "密码卡状态", qvQe);
        addScalar(EP + ".4.2.0", "cryptoCardCallCount", "Counter64", "密码卡调用次数", qvQe);
        addScalar(EP + ".4.3.0", "cryptoCardAlgorithms", "OctetString", "密码卡算法列表", qvQe);
        addScalar(EP + ".4.4.0", "cryptoCardModel", "OctetString", "密码卡型号", qvQe);
        addScalar(EP + ".4.5.0", "cryptoCardErrorCount", "Counter32", "密码卡错误计数", qvQe);
        addScalar(EP + ".4.6.0", "cryptoCardCompliance", "Integer32", "密码卡合规状态", qvQe);

        // IKE SA 数量
        addScalar(EP + ".5.0.0", "ikeCount", "Integer32", "IKE SA数量", qvTp);

        // 防火墙与路由
        addScalar(EP + ".7.1.0", "firewallRuleCount", "Integer32", "防火墙规则数", qvTp);
        addScalar(EP + ".7.2.0", "firewallAclCount", "Integer32", "ACL条目数", qvTp);
        addScalar(EP + ".7.3.0", "firewallSnatCount", "Integer32", "SNAT条目数", qvTp);
        addScalar(EP + ".7.4.0", "firewallDnatCount", "Integer32", "DNAT条目数", qvTp);
        addScalar(EP + ".7.5.0", "firewallWhitelistCount", "Integer32", "白名单条目数", qvTp);
        addScalar(EP + ".7.6.0", "routeIpv4Count", "Integer32", "IPv4路由条目数", qvTp);
        addScalar(EP + ".7.7.0", "routeIpv6Count", "Integer32", "IPv6路由条目数", qvTp);
        addScalar(EP + ".7.8.0", "routeTotalCount", "Integer32", "路由总条目数", qvTp);

        // 下发配置与限流（可写）
        addWritableScalar(EP + ".8.1.0", "deviceConfig", "OctetString", "设备配置下发", null);
        addWritableScalar(EP + ".8.2.0", "rateLimitRule", "OctetString", "限流规则下发", null);
    }

    private void addScalar(String oid, String name, String valueType, String descZh, Set<String> deviceTypes) {
        allScalarOids.add(new OidDefinition(oid, name, valueType, descZh, false, null, deviceTypes, false));
    }

    private void addWritableScalar(String oid, String name, String valueType, String descZh, Set<String> deviceTypes) {
        allScalarOids.add(new OidDefinition(oid, name, valueType, descZh, false, null, deviceTypes, true));
    }

    private void initTableColumns() {
        // ifTable columns: 1.3.6.1.2.1.2.2.1.{col}.{idx}
        ifTableColumns = new ArrayList<>();
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.1", "ifIndex", "Integer32", "接口索引", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.2", "ifDescr", "OctetString", "接口描述", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.3", "ifType", "Integer32", "接口类型", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.5", "ifSpeed", "Gauge32", "接口速率", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.7", "ifAdminStatus", "Integer32", "管理状态", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.8", "ifOperStatus", "Integer32", "操作状态", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.10", "ifInOctets", "Counter32", "入方向字节(32位)", true, "interface", null, false));
        ifTableColumns.add(new OidDefinition("1.3.6.1.2.1.2.2.1.16", "ifOutOctets", "Counter32", "出方向字节(32位)", true, "interface", null, false));

        // ifXTable columns: 1.3.6.1.2.1.31.1.1.1.{col}.{idx}
        ifxTableColumns = new ArrayList<>();
        ifxTableColumns.add(new OidDefinition("1.3.6.1.2.1.31.1.1.1.1", "ifName", "OctetString", "接口名称", true, "interface", null, false));
        ifxTableColumns.add(new OidDefinition("1.3.6.1.2.1.31.1.1.1.6", "ifHCInOctets", "Counter64", "入方向字节(64位)", true, "interface", null, false));
        ifxTableColumns.add(new OidDefinition("1.3.6.1.2.1.31.1.1.1.10", "ifHCOutOctets", "Counter64", "出方向字节(64位)", true, "interface", null, false));
        ifxTableColumns.add(new OidDefinition("1.3.6.1.2.1.31.1.1.1.15", "ifHighSpeed", "Gauge32", "接口高速速率(Mbps)", true, "interface", null, false));

        // Tunnel table columns: EP.2.1.1.{col}.{idx}
        Set<String> qvTp = new HashSet<>(Arrays.asList("quantum_vpn", "third_party_vpn"));
        tunnelTableColumns = new ArrayList<>();
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.1", "tunnelIndex", "Integer32", "隧道索引", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.2", "tunnelName", "OctetString", "隧道名称", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.3", "tunnelStatus", "Integer32", "隧道状态", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.4", "tunnelInOctets", "Counter64", "隧道入方向字节", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.5", "tunnelOutOctets", "Counter64", "隧道出方向字节", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.6", "tunnelLocalAddr", "OctetString", "隧道本端地址", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.7", "tunnelRemoteAddr", "OctetString", "隧道对端地址", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.8", "tunnelIkeRuleName", "OctetString", "IKE策略名称", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.9", "tunnelEncryptAlgo", "OctetString", "隧道加密算法", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.10", "tunnelKeySource", "Integer32", "密钥来源", true, "tunnel", qvTp, false));
        tunnelTableColumns.add(new OidDefinition(EP + ".2.1.1.11", "tunnelRateLimitRule", "OctetString", "隧道限流规则", true, "tunnel", qvTp, true));

        // IKE SA table columns: EP.5.1.1.{col}.{idx}
        ikeSaTableColumns = new ArrayList<>();
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.1", "ikeIndex", "Integer32", "IKE SA索引", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.2", "ikeLocalAddr", "OctetString", "IKE本端地址", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.3", "ikeRemoteAddr", "OctetString", "IKE对端地址", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.4", "ikeStatus", "Integer32", "IKE SA状态", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.5", "ikeVersion", "Integer32", "IKE版本", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.6", "ikeAuthMethod", "Integer32", "认证方式", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.7", "ikeEncryptAlgo", "OctetString", "IKE加密算法", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.8", "ikeHashAlgo", "OctetString", "哈希算法", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.9", "ikeDhGroup", "OctetString", "DH组", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.10", "ikeRekeyRemain", "Integer32", "重密钥剩余时间", true, "ike_sa", qvTp, false));
        ikeSaTableColumns.add(new OidDefinition(EP + ".5.1.1.11", "ikeDpdEnabled", "Integer32", "DPD是否启用", true, "ike_sa", qvTp, false));

        // IPsec SA table columns: EP.6.1.1.{col}.{idx}
        ipsecSaTableColumns = new ArrayList<>();
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.1", "ipsecIndex", "Integer32", "IPsec SA索引", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.2", "ipsecTunnelId", "Integer32", "关联隧道ID", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.3", "ipsecProtocol", "Integer32", "IPsec协议", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.4", "ipsecEncryptAlgo", "OctetString", "IPsec加密算法", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.5", "ipsecAuthAlgo", "OctetString", "IPsec认证算法", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.6", "ipsecKeySource", "Integer32", "密钥来源", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.7", "ipsecRekeyRemain", "Integer32", "重密钥剩余时间", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.8", "ipsecWorkMode", "Integer32", "工作模式", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.9", "ipsecEstablishTime", "TimeTicks", "SA建立时间", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.10", "ipsecInBytes", "Counter64", "入方向字节", true, "ipsec_sa", qvTp, false));
        ipsecSaTableColumns.add(new OidDefinition(EP + ".6.1.1.11", "ipsecOutBytes", "Counter64", "出方向字节", true, "ipsec_sa", qvTp, false));
    }

    /**
     * 根据厂家、设备类型、设备型号获取对应的OID列表
     */
    public List<OidDefinition> getOidList(String manufacturer, String deviceType, String deviceModel) {
        String cacheKey = manufacturer + ":" + deviceType + ":" + deviceModel;
        return oidCache.computeIfAbsent(cacheKey, k -> buildOidList(deviceType));
    }

    /**
     * 获取标量OID列表
     */
    public List<OidDefinition> getScalarOids(String deviceType) {
        List<OidDefinition> result = new ArrayList<>();
        for (OidDefinition oid : allScalarOids) {
            if (oid.getDeviceTypes() == null || oid.getDeviceTypes().contains(deviceType)) {
                result.add(oid);
            }
        }
        return result;
    }

    /**
     * 高频采集：仅 CPU/内存/磁盘/密码卡相关标量（按设备类型过滤私有 MIB）。
     */
    public List<OidDefinition> getMetricsScalarOids(String deviceType) {
        List<OidDefinition> result = new ArrayList<>();
        for (OidDefinition oid : allScalarOids) {
            if (!METRICS_SCALAR_NAMES.contains(oid.getName())) {
                continue;
            }
            if (oid.getDeviceTypes() == null || oid.getDeviceTypes().contains(deviceType)) {
                result.add(oid);
            }
        }
        return result;
    }

    /**
     * 获取所有标量OID（不区分设备类型）
     */
    public List<OidDefinition> getAllScalarOids() {
        return Collections.unmodifiableList(allScalarOids);
    }

    /**
     * 获取ifTable列定义
     */
    public List<OidDefinition> getIfTableColumns() {
        return Collections.unmodifiableList(ifTableColumns);
    }

    /**
     * 获取ifXTable列定义
     */
    public List<OidDefinition> getIfxTableColumns() {
        return Collections.unmodifiableList(ifxTableColumns);
    }

    /**
     * 获取隧道表列定义
     */
    public List<OidDefinition> getTunnelTableColumns() {
        return Collections.unmodifiableList(tunnelTableColumns);
    }

    /**
     * 获取IKE SA表列定义
     */
    public List<OidDefinition> getIkeSaTableColumns() {
        return Collections.unmodifiableList(ikeSaTableColumns);
    }

    /**
     * 获取IPsec SA表列定义
     */
    public List<OidDefinition> getIpsecSaTableColumns() {
        return Collections.unmodifiableList(ipsecSaTableColumns);
    }

    private List<OidDefinition> buildOidList(String deviceType) {
        List<OidDefinition> result = new ArrayList<>();
        result.addAll(getScalarOids(deviceType));
        result.addAll(ifTableColumns);
        result.addAll(ifxTableColumns);
        if ("quantum_vpn".equals(deviceType) || "third_party_vpn".equals(deviceType)) {
            result.addAll(tunnelTableColumns);
            result.addAll(ikeSaTableColumns);
            result.addAll(ipsecSaTableColumns);
        }
        return result;
    }

    /**
     * 将VPN-Sim的设备类型映射到内部类型
     */
    public static String mapDeviceType(String deviceType) {
        if (deviceType == null) return "quantum_vpn";
        switch (deviceType.toUpperCase()) {
            case "QVPN":
                return "quantum_vpn";
            case "QSW":
                return "quantum_encryptor";
            default:
                return deviceType.toLowerCase();
        }
    }
}
