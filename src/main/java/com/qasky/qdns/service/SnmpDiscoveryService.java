package com.qasky.qdns.service;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.SnmpResult;
import com.qasky.qdns.model.dto.DiscoverRequest;
import com.qasky.qdns.model.dto.DiscoveredHost;
import com.qasky.qdns.snmp.SnmpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 基于 UDP/SNMP 的 IPv4 网段发现
 */
@Service
public class SnmpDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(SnmpDiscoveryService.class);

    private static final int MAX_HOSTS = 1024;
    private static final String OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0";
    private static final String OID_SYS_NAME = "1.3.6.1.2.1.1.5.0";

    private final SnmpClient snmpClient;

    public SnmpDiscoveryService(SnmpClient snmpClient) {
        this.snmpClient = snmpClient;
    }

    /**
     * 扫描网段内对 SNMP 有响应的主机
     */
    public List<DiscoveredHost> discover(DiscoverRequest req) {
        Objects.requireNonNull(req.getNetworkAddress(), "networkAddress");
        Objects.requireNonNull(req.getPrefixLength(), "prefixLength");

        int prefix = req.getPrefixLength();
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("prefixLength 必须在 0～32 之间");
        }

        String netAddr = req.getNetworkAddress().trim();
        long hostCount = countScanHosts(netAddr, prefix);
        if (hostCount > MAX_HOSTS) {
            throw new IllegalArgumentException(
                    "可扫描主机数 " + hostCount + " 超过上限 " + MAX_HOSTS + "，请缩小网段");
        }
        List<String> ips = expandIpv4Hosts(netAddr, prefix);

        int port = req.getSnmpPort() != null ? req.getSnmpPort() : 161;
        int timeoutMs = req.getTimeoutMs() != null ? req.getTimeoutMs() : 500;
        int retries = req.getRetries() != null ? req.getRetries() : 0;
        int maxConcurrent = req.getMaxConcurrent() != null ? req.getMaxConcurrent() : 32;
        maxConcurrent = Math.min(Math.max(maxConcurrent, 1), 64);

        String protocol = req.getProtocol() != null ? req.getProtocol() : "SNMPv2c";
        String portStr = String.valueOf(port);
        long futureWaitMs = "SNMPv3".equalsIgnoreCase(protocol)
                ? 20000L
                : Math.max(timeoutMs, 300) * 4L + 2000L;

        List<String> oids = Arrays.asList(OID_SYS_DESCR, OID_SYS_NAME);
        DeviceInfo v3Discover = buildV3DiscoverContext(req);
        ExecutorService pool = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<DiscoveredHost>> futures = new ArrayList<>();

        try {
            for (String ip : ips) {
                futures.add(pool.submit(() -> probe(ip, portStr, port, oids, protocol,
                        req.getCommunity(), timeoutMs, retries, v3Discover)));
            }

            List<DiscoveredHost> found = new ArrayList<>();
            for (Future<DiscoveredHost> f : futures) {
                try {
                    DiscoveredHost h = f.get(futureWaitMs, TimeUnit.MILLISECONDS);
                    if (h != null) {
                        found.add(h);
                    }
                } catch (TimeoutException e) {
                    f.cancel(true);
                } catch (ExecutionException e) {
                    log.debug("探测任务异常: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            found.sort(Comparator.comparing(h -> ipToLong(h.getDeviceIp())));
            return found;
        } finally {
            pool.shutdownNow();
        }
    }

    private DiscoveredHost probe(String ip, String portStr, int port, List<String> oids, String protocol,
                                 String community, int timeoutMs, int retries, DeviceInfo v3Discover) {
        List<SnmpResult> results;
        if ("SNMPv3".equalsIgnoreCase(protocol)) {
            results = snmpClient.get(ip, port, oids, protocol, v3Discover);
        } else {
            results = snmpClient.getV2c(ip, port, oids, community, timeoutMs, retries);
        }

        if (results == null || results.isEmpty()) {
            return null;
        }
        boolean anyOk = false;
        String sysDescr = null;
        String sysName = null;
        for (SnmpResult r : results) {
            if (r.isSuccess()) {
                anyOk = true;
                if (OID_SYS_DESCR.equals(r.getOid())) {
                    sysDescr = r.getValue();
                } else if (OID_SYS_NAME.equals(r.getOid())) {
                    sysName = r.getValue();
                }
            }
        }
        if (!anyOk) {
            return null;
        }
        return DiscoveredHost.builder()
                .deviceIp(ip)
                .devicePort(portStr)
                .protocol(protocol)
                .sysDescr(sysDescr)
                .sysName(sysName)
                .build();
    }

    private static DeviceInfo buildV3DiscoverContext(DiscoverRequest req) {
        if (req.getProtocol() == null || !"SNMPv3".equalsIgnoreCase(req.getProtocol())) {
            return null;
        }
        boolean any = hasText(req.getV3Username()) || hasText(req.getV3AuthPassword()) || hasText(req.getV3PrivPassword())
                || hasText(req.getV3AuthProtocol()) || hasText(req.getV3PrivProtocol());
        if (!any) {
            return null;
        }
        DeviceInfo d = new DeviceInfo();
        d.setSnmpV3Username(req.getV3Username());
        d.setSnmpV3AuthPassword(req.getV3AuthPassword());
        d.setSnmpV3PrivPassword(req.getV3PrivPassword());
        d.setSnmpV3AuthProtocol(req.getV3AuthProtocol());
        d.setSnmpV3PrivProtocol(req.getV3PrivProtocol());
        return d;
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private long ipToLong(String ip) {
        try {
            byte[] a = InetAddress.getByName(ip).getAddress();
            if (a.length != 4) {
                return 0;
            }
            return ((a[0] & 0xffL) << 24) | ((a[1] & 0xffL) << 16) | ((a[2] & 0xffL) << 8) | (a[3] & 0xffL);
        } catch (UnknownHostException e) {
            return 0;
        }
    }

    /**
     * 待扫描主机数量（不展开列表，避免大网段 OOM）
     */
    static long countScanHosts(String networkAddress, int prefixLength) {
        long[] nb = networkAndBroadcast(networkAddress, prefixLength);
        long size = nb[1] - nb[0] + 1;
        if (size <= 2) {
            return size;
        }
        return size - 2;
    }

    private static long[] networkAndBroadcast(String networkAddress, int prefixLength) {
        byte[] raw;
        try {
            raw = InetAddress.getByName(networkAddress).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无效的网络地址: " + networkAddress);
        }
        if (raw.length != 4) {
            throw new IllegalArgumentException("当前仅支持 IPv4");
        }
        long ip = ((raw[0] & 0xffL) << 24) | ((raw[1] & 0xffL) << 16) | ((raw[2] & 0xffL) << 8) | (raw[3] & 0xffL);
        long mask = prefixLength == 0 ? 0L : (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
        long network = ip & mask;
        long broadcast = network | (~mask & 0xffffffffL);
        return new long[]{network, broadcast};
    }

    /**
     * 根据网络地址与前缀长度展开待探测的主机 IPv4 列表（/32、/31 全量；其它跳过网络地址与广播地址）
     */
    static List<String> expandIpv4Hosts(String networkAddress, int prefixLength) {
        long[] nb = networkAndBroadcast(networkAddress, prefixLength);
        long network = nb[0];
        long broadcast = nb[1];
        long size = broadcast - network + 1;

        List<String> out = new ArrayList<>();
        if (size <= 0) {
            return out;
        }
        if (size == 1) {
            out.add(formatIpv4(network));
            return out;
        }
        if (size == 2) {
            out.add(formatIpv4(network));
            out.add(formatIpv4(broadcast));
            return out;
        }
        for (long h = network + 1; h < broadcast; h++) {
            out.add(formatIpv4(h));
        }
        return out;
    }

    private static String formatIpv4(long v) {
        return String.format("%d.%d.%d.%d",
                (v >> 24) & 0xff, (v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff);
    }
}
