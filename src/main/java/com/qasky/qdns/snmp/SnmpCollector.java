package com.qasky.qdns.snmp;

import com.qasky.qdns.config.OidRegistry;
import com.qasky.qdns.config.SnmpConfig;
import com.qasky.qdns.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SNMP采集器 - 将SNMP原始数据转换为DeviceStatus结构化数据
 */
@Component
public class SnmpCollector {

    private static final Logger log = LoggerFactory.getLogger(SnmpCollector.class);

    private final SnmpClient snmpClient;
    private final OidRegistry oidRegistry;
    private final SnmpConfig snmpConfig;

    public SnmpCollector(SnmpClient snmpClient, OidRegistry oidRegistry, SnmpConfig snmpConfig) {
        this.snmpClient = snmpClient;
        this.oidRegistry = oidRegistry;
        this.snmpConfig = snmpConfig;
    }

    /**
     * 完整采集一台设备的状态（全量模式）
     */
    public DeviceStatus collectDevice(DeviceInfo device) {
        return collectDevice(device, CollectMode.FULL);
    }

    /**
     * 按模式采集一台设备
     */
    public DeviceStatus collectDevice(DeviceInfo device, CollectMode mode) {
        DeviceStatus status = new DeviceStatus();
        status.setDeviceId(device.getId());
        status.setDeviceIp(device.getDeviceIp());
        status.setSnmpPort(parsePort(device.getDevicePort()));
        status.setCollectTime(new Date());

        String protocol = resolveProtocol(device.getProtocol());
        String host = device.getDeviceIp();
        int port = status.getSnmpPort();

        // 1. 检测设备是否在线（SNMPv3 时使用设备上的凭据覆盖全局配置）
        if (!snmpClient.isDeviceOnline(host, port, protocol, device)) {
            status.setOnline(false);
            status.setErrorMessage("Device unreachable via SNMP");
            log.warn("设备不在线: {} ({}:{})", device.getName(), host, port);
            return status;
        }
        status.setOnline(true);

        String deviceType = OidRegistry.mapDeviceType(device.getDeviceType());

        if (mode == CollectMode.METRICS_ONLY) {
            collectScalarOidsFromDefinitions(status, host, port, protocol, deviceType, device,
                    oidRegistry.getMetricsScalarOids(deviceType));
            if ("quantum_vpn".equals(deviceType) || "third_party_vpn".equals(deviceType)) {
                collectTunnelTable(status, host, port, protocol, device);
            }
            calculateDerivedValues(status);
            log.debug("设备高频采集完成: {} ({}) cpu={}% mem={}% disk={}%",
                    device.getName(), host,
                    status.getCpuUsage(), status.getMemUsagePercent(), status.getDiskPercent());
            return status;
        }

        // FULL：标量 + 接口表 + VPN 表
        collectScalarOids(status, host, port, protocol, deviceType, device);
        collectInterfaceTable(status, host, port, protocol, device);
        if ("quantum_vpn".equals(deviceType) || "third_party_vpn".equals(deviceType)) {
            collectTunnelTable(status, host, port, protocol, device);
            collectIkeSaTable(status, host, port, protocol, device);
            collectIpsecSaTable(status, host, port, protocol, device);
        }
        calculateDerivedValues(status);

        log.info("设备采集完成: {} ({}) online={} cpu={}% mem={}% disk={}%",
                device.getName(), host, status.isOnline(),
                status.getCpuUsage(), status.getMemUsagePercent(), status.getDiskPercent());

        return status;
    }

    /**
     * 采集单个OID值
     */
    public SnmpResult collectSingleOid(String host, int port, String oid, String protocol) {
        return collectSingleOid(host, port, oid, protocol, null, null);
    }

    public SnmpResult collectSingleOid(String host, int port, String oid, String protocol, DeviceInfo v3Device) {
        return collectSingleOid(host, port, oid, protocol, v3Device, null);
    }

    public SnmpResult collectSingleOid(String host, int port, String oid, String protocol, DeviceInfo v3Device, String community) {
        return snmpClient.get(host, port, oid, protocol, v3Device, community);
    }

    /**
     * SNMP Walk
     */
    public List<SnmpResult> walkOid(String host, int port, String rootOid, String protocol) {
        return walkOid(host, port, rootOid, protocol, null, null);
    }

    public List<SnmpResult> walkOid(String host, int port, String rootOid, String protocol, DeviceInfo v3Device) {
        return walkOid(host, port, rootOid, protocol, v3Device, null);
    }

    public List<SnmpResult> walkOid(String host, int port, String rootOid, String protocol, DeviceInfo v3Device, String community) {
        return snmpClient.walk(host, port, rootOid, protocol, v3Device, community);
    }

    private void collectScalarOids(DeviceStatus status, String host, int port,
                                   String protocol, String deviceType, DeviceInfo device) {
        collectScalarOidsFromDefinitions(status, host, port, protocol, deviceType, device,
                oidRegistry.getScalarOids(deviceType));
    }

    private void collectScalarOidsFromDefinitions(DeviceStatus status, String host, int port,
                                                  String protocol, String deviceType, DeviceInfo device,
                                                  List<OidDefinition> scalarOids) {
        if (scalarOids.isEmpty()) {
            return;
        }
        List<String> oids = scalarOids.stream()
                .map(OidDefinition::getOid)
                .collect(Collectors.toList());

        List<SnmpResult> results = snmpClient.batchGet(host, port, oids, protocol, 20, device);

        Map<String, String> rawData = status.getRawOidData() != null
                ? new LinkedHashMap<>(status.getRawOidData())
                : new LinkedHashMap<>();
        Map<String, String> oidToName = new HashMap<>();
        for (OidDefinition def : scalarOids) {
            oidToName.put(def.getOid(), def.getName());
        }

        for (SnmpResult r : results) {
            if (r.isSuccess()) {
                String name = oidToName.getOrDefault(r.getOid(), r.getOid());
                rawData.put(name, r.getValue());
                mapScalarToStatus(status, name, r.getValue());
            }
        }
        status.setRawOidData(rawData);
    }

    private void mapScalarToStatus(DeviceStatus status, String name, String value) {
        try {
            switch (name) {
                case "sysDescr":
                    status.setSysDescr(value);
                    break;
                case "sysObjectID":
                    status.setSysObjectID(value);
                    break;
                case "sysUpTime":
                    status.setSysUpTime(parseLong(value));
                    break;
                case "sysContact":
                    status.setSysContact(value);
                    break;
                case "sysName":
                    status.setSysName(value);
                    break;
                case "sysLocation":
                    status.setSysLocation(value);
                    break;
                case "ifNumber":
                    status.setIfNumber(parseInt(value));
                    break;
                case "ssCpuUser":
                    status.setCpuUsage(parseInt(value));
                    break;
                case "ssCpuIdle":
                    status.setCpuIdle(parseInt(value));
                    break;
                case "memTotalReal":
                    status.setMemTotalKb(parseLong(value));
                    break;
                case "memAvailReal":
                    status.setMemAvailKb(parseLong(value));
                    break;
                case "dskPercent":
                    status.setDiskPercent(parseInt(value));
                    break;
                case "vpnDeviceType":
                    status.setVpnDeviceType(parseInt(value));
                    break;
                case "vpnFirmwareVersion":
                    status.setVpnFirmwareVersion(value);
                    break;
                case "vpnSerialNumber":
                    status.setVpnSerialNumber(value);
                    break;
                case "vpnDeviceStatus":
                    status.setVpnDeviceStatus(parseInt(value));
                    break;
                case "vpnMacAddress":
                    status.setVpnMacAddress(value);
                    break;
                case "vpnDeviceModel":
                    status.setVpnDeviceModel(value);
                    break;
                case "vpnVendor":
                    status.setVpnVendor(value);
                    break;
                case "tunnelCount":
                    status.setTunnelCount(parseInt(value));
                    break;
                case "wirelessStatus":
                    status.setWirelessStatus(parseInt(value));
                    break;
                case "wirelessInOctets":
                    status.setWirelessInOctets(parseLong(value));
                    break;
                case "wirelessOutOctets":
                    status.setWirelessOutOctets(parseLong(value));
                    break;
                case "cryptoCardStatus":
                    status.setCryptoCardStatus(parseInt(value));
                    break;
                case "cryptoCardCallCount":
                    status.setCryptoCardCallCount(parseLong(value));
                    break;
                case "cryptoCardAlgorithms":
                    status.setCryptoCardAlgorithms(value);
                    break;
                case "cryptoCardModel":
                    status.setCryptoCardModel(value);
                    break;
                case "cryptoCardErrorCount":
                    status.setCryptoCardErrorCount(parseLong(value));
                    break;
                case "cryptoCardCompliance":
                    status.setCryptoCardCompliance(parseInt(value));
                    break;
                case "ikeCount":
                    status.setIkeCount(parseInt(value));
                    break;
                case "firewallRuleCount":
                    status.setFirewallRuleCount(parseInt(value));
                    break;
                case "firewallAclCount":
                    status.setFirewallAclCount(parseInt(value));
                    break;
                case "firewallSnatCount":
                    status.setFirewallSnatCount(parseInt(value));
                    break;
                case "firewallDnatCount":
                    status.setFirewallDnatCount(parseInt(value));
                    break;
                case "firewallWhitelistCount":
                    status.setFirewallWhitelistCount(parseInt(value));
                    break;
                case "routeIpv4Count":
                    status.setRouteIpv4Count(parseInt(value));
                    break;
                case "routeIpv6Count":
                    status.setRouteIpv6Count(parseInt(value));
                    break;
                case "routeTotalCount":
                    status.setRouteTotalCount(parseInt(value));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.debug("映射OID值失败: {} = {} - {}", name, value, e.getMessage());
        }
    }

    private void collectInterfaceTable(DeviceStatus status, String host, int port, String protocol, DeviceInfo device) {
        // Walk ifTable
        List<SnmpResult> ifResults = snmpClient.walk(host, port, "1.3.6.1.2.1.2.2.1", protocol, device);
        // Walk ifXTable
        List<SnmpResult> ifxResults = snmpClient.walk(host, port, "1.3.6.1.2.1.31.1.1.1", protocol, device);
        // Walk ipAddrTable
        List<SnmpResult> ipAddrResults = snmpClient.walk(host, port, "1.3.6.1.2.1.4.20.1", protocol, device);

        Map<Integer, DeviceStatus.InterfaceStatus> ifMap = new TreeMap<>();

        parseIfTableResults(ifResults, ifMap, "1.3.6.1.2.1.2.2.1.");
        parseIfxTableResults(ifxResults, ifMap, "1.3.6.1.2.1.31.1.1.1.");
        parseIpAddrTableResults(ipAddrResults, ifMap, "1.3.6.1.2.1.4.20.1.");

        status.setInterfaces(new ArrayList<>(ifMap.values()));
    }

    private void parseIfTableResults(List<SnmpResult> results,
                                     Map<Integer, DeviceStatus.InterfaceStatus> ifMap,
                                     String prefix) {
        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.", 2);
            if (parts.length < 2) continue;
            int col = parseInt(parts[0]);
            int idx = parseInt(parts[1]);
            if (idx <= 0) continue;

            DeviceStatus.InterfaceStatus iface = ifMap.computeIfAbsent(idx, k -> {
                DeviceStatus.InterfaceStatus s = new DeviceStatus.InterfaceStatus();
                s.setIndex(k);
                return s;
            });

            switch (col) {
                case 1: iface.setIndex(parseInt(r.getValue())); break;
                case 2: iface.setName(r.getValue()); break;
                case 3: iface.setIfType(parseInt(r.getValue())); break;
                case 5: iface.setSpeed(parseLong(r.getValue())); break;
                case 6: iface.setMacAddress(r.getValue()); break;
                case 7: iface.setAdminStatus(parseInt(r.getValue())); break;
                case 8: iface.setOperStatus(parseInt(r.getValue())); break;
                case 10: iface.setInOctets(parseLong(r.getValue())); break;
                case 16: iface.setOutOctets(parseLong(r.getValue())); break;
                default: break;
            }
        }
    }

    private void parseIpAddrTableResults(List<SnmpResult> results,
                                         Map<Integer, DeviceStatus.InterfaceStatus> ifMap,
                                         String prefix) {
        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.");
            if (parts.length < 5) continue;

            int col = parseInt(parts[0]);
            String ip = parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4];
            if (col != 2) {
                continue;
            }

            int ifIndex = parseInt(r.getValue());
            if (ifIndex <= 0) continue;

            DeviceStatus.InterfaceStatus iface = ifMap.computeIfAbsent(ifIndex, k -> {
                DeviceStatus.InterfaceStatus s = new DeviceStatus.InterfaceStatus();
                s.setIndex(k);
                return s;
            });
            iface.setIpAddress(ip);
        }
    }

    private void parseIfxTableResults(List<SnmpResult> results,
                                      Map<Integer, DeviceStatus.InterfaceStatus> ifMap,
                                      String prefix) {
        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.", 2);
            if (parts.length < 2) continue;
            int col = parseInt(parts[0]);
            int idx = parseInt(parts[1]);
            if (idx <= 0) continue;

            DeviceStatus.InterfaceStatus iface = ifMap.computeIfAbsent(idx, k -> {
                DeviceStatus.InterfaceStatus s = new DeviceStatus.InterfaceStatus();
                s.setIndex(k);
                return s;
            });

            switch (col) {
                case 1: iface.setName(r.getValue()); break;
                case 6: iface.setHcInOctets(parseLong(r.getValue())); break;
                case 10: iface.setHcOutOctets(parseLong(r.getValue())); break;
                case 15: iface.setHighSpeed(parseLong(r.getValue())); break;
                default: break;
            }
        }
    }

    private void collectTunnelTable(DeviceStatus status, String host, int port, String protocol, DeviceInfo device) {
        String ep = "1.3.6.1.4.1.99999";
        List<SnmpResult> results = snmpClient.walk(host, port, ep + ".2.1.1", protocol, device);

        Map<Integer, DeviceStatus.TunnelStatus> tunMap = new TreeMap<>();
        String prefix = ep + ".2.1.1.";

        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.", 2);
            if (parts.length < 2) continue;
            int col = parseInt(parts[0]);
            int idx = parseInt(parts[1]);
            if (idx <= 0) continue;

            DeviceStatus.TunnelStatus tunnel = tunMap.computeIfAbsent(idx, k -> {
                DeviceStatus.TunnelStatus s = new DeviceStatus.TunnelStatus();
                s.setIndex(k);
                return s;
            });

            switch (col) {
                case 1: tunnel.setIndex(parseInt(r.getValue())); break;
                case 2: tunnel.setName(r.getValue()); break;
                case 3: tunnel.setStatus(parseInt(r.getValue())); break;
                case 4: tunnel.setInOctets(parseLong(r.getValue())); break;
                case 5: tunnel.setOutOctets(parseLong(r.getValue())); break;
                case 6: tunnel.setLocalAddr(r.getValue()); break;
                case 7: tunnel.setRemoteAddr(r.getValue()); break;
                case 8: tunnel.setIkeRuleName(r.getValue()); break;
                case 9: tunnel.setEncryptAlgo(r.getValue()); break;
                case 10: tunnel.setKeySource(parseInt(r.getValue())); break;
                case 12: tunnel.setCurrentRateBps(parseLong(r.getValue())); break;
                case 13: tunnel.setThroughputBytes(parseLong(r.getValue())); break;
                default: break;
            }
        }
        status.setTunnels(new ArrayList<>(tunMap.values()));
    }

    private void collectIkeSaTable(DeviceStatus status, String host, int port, String protocol, DeviceInfo device) {
        String ep = "1.3.6.1.4.1.99999";
        List<SnmpResult> results = snmpClient.walk(host, port, ep + ".5.1.1", protocol, device);

        Map<Integer, DeviceStatus.IkeSaStatus> ikeMap = new TreeMap<>();
        String prefix = ep + ".5.1.1.";

        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.", 2);
            if (parts.length < 2) continue;
            int col = parseInt(parts[0]);
            int idx = parseInt(parts[1]);
            if (idx <= 0) continue;

            DeviceStatus.IkeSaStatus ike = ikeMap.computeIfAbsent(idx, k -> {
                DeviceStatus.IkeSaStatus s = new DeviceStatus.IkeSaStatus();
                s.setIndex(k);
                return s;
            });

            switch (col) {
                case 1: ike.setIndex(parseInt(r.getValue())); break;
                case 2: ike.setLocalAddr(r.getValue()); break;
                case 3: ike.setRemoteAddr(r.getValue()); break;
                case 4: ike.setStatus(parseInt(r.getValue())); break;
                case 5: ike.setVersion(parseInt(r.getValue())); break;
                case 6: ike.setAuthMethod(parseInt(r.getValue())); break;
                case 7: ike.setEncryptAlgo(r.getValue()); break;
                case 8: ike.setHashAlgo(r.getValue()); break;
                case 9: ike.setDhGroup(r.getValue()); break;
                case 10: ike.setRekeyRemain(parseInt(r.getValue())); break;
                case 11: ike.setDpdEnabled(parseInt(r.getValue())); break;
                default: break;
            }
        }
        status.setIkeSas(new ArrayList<>(ikeMap.values()));
    }

    private void collectIpsecSaTable(DeviceStatus status, String host, int port, String protocol, DeviceInfo device) {
        String ep = "1.3.6.1.4.1.99999";
        List<SnmpResult> results = snmpClient.walk(host, port, ep + ".6.1.1", protocol, device);

        Map<Integer, DeviceStatus.IpsecSaStatus> saMap = new TreeMap<>();
        String prefix = ep + ".6.1.1.";

        for (SnmpResult r : results) {
            if (!r.isSuccess()) continue;
            String oid = r.getOid();
            if (!oid.startsWith(prefix)) continue;
            String suffix = oid.substring(prefix.length());
            String[] parts = suffix.split("\\.", 2);
            if (parts.length < 2) continue;
            int col = parseInt(parts[0]);
            int idx = parseInt(parts[1]);
            if (idx <= 0) continue;

            DeviceStatus.IpsecSaStatus sa = saMap.computeIfAbsent(idx, k -> {
                DeviceStatus.IpsecSaStatus s = new DeviceStatus.IpsecSaStatus();
                s.setIndex(k);
                return s;
            });

            switch (col) {
                case 1: sa.setIndex(parseInt(r.getValue())); break;
                case 2: sa.setTunnelId(parseInt(r.getValue())); break;
                case 3: sa.setProtocol(parseInt(r.getValue())); break;
                case 4: sa.setEncryptAlgo(r.getValue()); break;
                case 5: sa.setAuthAlgo(r.getValue()); break;
                case 6: sa.setKeySource(parseInt(r.getValue())); break;
                case 7: sa.setRekeyRemain(parseInt(r.getValue())); break;
                case 8: sa.setWorkMode(parseInt(r.getValue())); break;
                case 9: sa.setEstablishTime(parseLong(r.getValue())); break;
                case 10: sa.setInBytes(parseLong(r.getValue())); break;
                case 11: sa.setOutBytes(parseLong(r.getValue())); break;
                default: break;
            }
        }
        status.setIpsecSas(new ArrayList<>(saMap.values()));
    }

    private void calculateDerivedValues(DeviceStatus status) {
        if (status.getMemTotalKb() > 0) {
            long used = status.getMemTotalKb() - status.getMemAvailKb();
            status.setMemUsagePercent((int) (used * 100 / status.getMemTotalKb()));
        }
    }

    private String resolveProtocol(String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            return snmpConfig.getDefaultProtocol();
        }
        if (protocol.toUpperCase().contains("V3") || protocol.toUpperCase().contains("SNMPV3")) {
            return "SNMPv3";
        }
        if (protocol.toUpperCase().contains("V2") || protocol.toUpperCase().contains("SNMPV2")) {
            return "SNMPv2c";
        }
        return snmpConfig.getDefaultProtocol();
    }

    private int parsePort(String port) {
        try {
            return Integer.parseInt(port);
        } catch (Exception e) {
            return 161;
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
