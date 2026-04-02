package com.qasky.qdns.service;

import com.qasky.qdns.config.CollectorConfig;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.DeviceStatus;
import com.qasky.qdns.snmp.CollectMode;
import com.qasky.qdns.snmp.SnmpCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备采集服务 - 管理采集任务，提供手动和定时采集
 */
@Service
public class DeviceCollectorService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCollectorService.class);

    private final SnmpCollector snmpCollector;
    private final RedisDeviceService redisDeviceService;
    private final CollectorConfig collectorConfig;

    private final ExecutorService executorService;
    private final AtomicBoolean collecting = new AtomicBoolean(false);
    private final AtomicInteger lastCollectCount = new AtomicInteger(0);
    private volatile long lastCollectTime = 0;
    private volatile long lastCollectDuration = 0;
    private volatile long lastMetricsCollectTime = 0;
    private volatile long lastMetricsCollectDuration = 0;

    /** 内存中的设备列表（支持不依赖Redis直接添加设备） */
    private final ConcurrentHashMap<String, DeviceInfo> localDevices = new ConcurrentHashMap<>();
    /** 最近一次采集结果 */
    private final ConcurrentHashMap<String, DeviceStatus> latestStatus = new ConcurrentHashMap<>();

    public DeviceCollectorService(SnmpCollector snmpCollector,
                                  RedisDeviceService redisDeviceService,
                                  CollectorConfig collectorConfig) {
        this.snmpCollector = snmpCollector;
        this.redisDeviceService = redisDeviceService;
        this.collectorConfig = collectorConfig;
        this.executorService = Executors.newFixedThreadPool(
                collectorConfig.getThreadPoolSize(),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "snmp-collector-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );
    }

    /**
     * 执行一轮完整采集
     */
    public List<DeviceStatus> collectAll() {
        if (!collecting.compareAndSet(false, true)) {
            log.warn("采集任务正在进行中，跳过本次采集");
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        List<DeviceStatus> results = new ArrayList<>();

        try {
            // 合并Redis设备列表和本地设备列表
            List<DeviceInfo> devices = mergeDeviceLists();
            if (devices.isEmpty()) {
                log.info("无设备需要采集");
                return results;
            }

            log.info("开始采集 {} 台设备...", devices.size());

            // 并发采集
            List<Future<DeviceStatus>> futures = new ArrayList<>();
            for (DeviceInfo device : devices) {
                futures.add(executorService.submit(() -> {
                    try {
                        return snmpCollector.collectDevice(device);
                    } catch (Exception e) {
                        log.error("采集设备异常: {} ({})", device.getName(), device.getDeviceIp(), e);
                        DeviceStatus errStatus = new DeviceStatus();
                        errStatus.setDeviceId(device.getId());
                        errStatus.setDeviceIp(device.getDeviceIp());
                        errStatus.setOnline(false);
                        errStatus.setErrorMessage(e.getMessage());
                        errStatus.setCollectTime(new Date());
                        return errStatus;
                    }
                }));
            }

            // 收集结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    DeviceStatus status = futures.get(i).get(30, TimeUnit.SECONDS);
                    results.add(status);
                    latestStatus.put(status.getDeviceId(), status);

                    // 回写Redis
                    redisDeviceService.saveDeviceStatus(status);
                    redisDeviceService.updateDeviceInfo(devices.get(i), status);
                } catch (TimeoutException e) {
                    log.warn("设备采集超时: {}", devices.get(i).getDeviceIp());
                } catch (Exception e) {
                    log.error("获取采集结果异常", e);
                }
            }

            // 回写设备列表到Redis
            redisDeviceService.registerDevices(devices);

            lastCollectCount.set(results.size());
            lastCollectDuration = System.currentTimeMillis() - startTime;
            lastCollectTime = System.currentTimeMillis();

            long onlineCount = results.stream().filter(DeviceStatus::isOnline).count();
            log.info("全量采集完成: 总计 {} 台, 在线 {} 台, 耗时 {}ms",
                    results.size(), onlineCount, lastCollectDuration);

        } finally {
            collecting.set(false);
        }

        return results;
    }

    /**
     * 高频指标采集一轮：仅 SNMP 标量（无接口/VPN 表 walk），结果合并进已有状态后写 Redis。
     */
    public List<DeviceStatus> collectMetricsRound() {
        if (!collecting.compareAndSet(false, true)) {
            log.warn("采集任务正在进行中，跳过本次高频采集");
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        List<DeviceStatus> results = new ArrayList<>();

        try {
            List<DeviceInfo> devices = mergeDeviceLists();
            if (devices.isEmpty()) {
                log.debug("无设备需要高频采集");
                return results;
            }

            log.debug("开始高频采集 {} 台设备...", devices.size());

            List<Future<DeviceStatus>> futures = new ArrayList<>();
            for (DeviceInfo device : devices) {
                futures.add(executorService.submit(() -> {
                    try {
                        return snmpCollector.collectDevice(device, CollectMode.METRICS_ONLY);
                    } catch (Exception e) {
                        log.error("高频采集异常: {} ({})", device.getName(), device.getDeviceIp(), e);
                        DeviceStatus errStatus = new DeviceStatus();
                        errStatus.setDeviceId(device.getId());
                        errStatus.setDeviceIp(device.getDeviceIp());
                        errStatus.setSnmpPort(device.getDevicePort() != null ? parsePortSafe(device.getDevicePort()) : 161);
                        errStatus.setOnline(false);
                        errStatus.setErrorMessage(e.getMessage());
                        errStatus.setCollectTime(new Date());
                        return errStatus;
                    }
                }));
            }

            for (int i = 0; i < futures.size(); i++) {
                DeviceInfo device = devices.get(i);
                try {
                    DeviceStatus partial = futures.get(i).get(30, TimeUnit.SECONDS);
                    DeviceStatus merged = mergeMetricsWithBaseline(device, partial);
                    results.add(merged);
                    latestStatus.put(merged.getDeviceId(), merged);
                    redisDeviceService.saveDeviceStatus(merged);
                    redisDeviceService.updateDeviceInfo(device, merged);
                } catch (TimeoutException e) {
                    log.warn("高频采集超时: {}", device.getDeviceIp());
                } catch (Exception e) {
                    log.error("高频采集获取结果异常", e);
                }
            }

            lastMetricsCollectDuration = System.currentTimeMillis() - startTime;
            lastMetricsCollectTime = System.currentTimeMillis();
            long onlineCount = results.stream().filter(DeviceStatus::isOnline).count();
            log.info("高频采集完成: 总计 {} 台, 在线 {} 台, 耗时 {}ms",
                    results.size(), onlineCount, lastMetricsCollectDuration);

        } finally {
            collecting.set(false);
        }

        return results;
    }

    private static int parsePortSafe(String port) {
        try {
            return Integer.parseInt(port.trim());
        } catch (Exception e) {
            return 161;
        }
    }

    /**
     * 将高频采集结果合并到 Redis/内存中的全量基线；无基线时直接使用本次结果。
     */
    private DeviceStatus mergeMetricsWithBaseline(DeviceInfo device, DeviceStatus partial) {
        DeviceStatus base = redisDeviceService.getDeviceStatus(device.getId());
        if (base == null) {
            base = latestStatus.get(device.getId());
        }
        if (base == null) {
            return partial;
        }

        base.setOnline(partial.isOnline());
        base.setErrorMessage(partial.getErrorMessage());
        base.setCollectTime(partial.getCollectTime());
        base.setSnmpPort(partial.getSnmpPort());
        base.setDeviceIp(partial.getDeviceIp());

        if (partial.isOnline()) {
            base.setCpuUsage(partial.getCpuUsage());
            base.setCpuIdle(partial.getCpuIdle());
            base.setMemTotalKb(partial.getMemTotalKb());
            base.setMemAvailKb(partial.getMemAvailKb());
            base.setMemUsagePercent(partial.getMemUsagePercent());
            base.setDiskPercent(partial.getDiskPercent());
            base.setCryptoCardCallCount(partial.getCryptoCardCallCount());
            base.setCryptoCardAlgorithms(partial.getCryptoCardAlgorithms());
        }

        if (partial.getRawOidData() != null && !partial.getRawOidData().isEmpty()) {
            if (base.getRawOidData() == null) {
                base.setRawOidData(new LinkedHashMap<>(partial.getRawOidData()));
            } else {
                base.getRawOidData().putAll(partial.getRawOidData());
            }
        }

        return base;
    }

    /**
     * 采集单台设备
     */
    public DeviceStatus collectSingle(String deviceId) {
        DeviceInfo device = localDevices.get(deviceId);
        if (device == null) {
            List<DeviceInfo> redisDevices = redisDeviceService.getAssignedDevices();
            for (DeviceInfo d : redisDevices) {
                if (d.getId().equals(deviceId)) {
                    device = d;
                    break;
                }
            }
        }
        if (device == null) {
            return null;
        }

        DeviceStatus status = snmpCollector.collectDevice(device);
        latestStatus.put(status.getDeviceId(), status);
        redisDeviceService.saveDeviceStatus(status);
        redisDeviceService.updateDeviceInfo(device, status);
        return status;
    }

    /**
     * 添加本地设备（测试用，无需Redis）
     */
    public void addLocalDevice(DeviceInfo device) {
        if (device.getId() == null || device.getId().isEmpty()) {
            device.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        localDevices.put(device.getId(), device);
        log.info("添加本地设备: {} ({}) id={}", device.getName(), device.getDeviceIp(), device.getId());
    }

    /**
     * 移除本地设备
     */
    public void removeLocalDevice(String deviceId) {
        DeviceInfo removed = localDevices.remove(deviceId);
        if (removed != null) {
            log.info("移除本地设备: {} ({})", removed.getName(), removed.getDeviceIp());
        }
    }

    /**
     * 获取所有设备列表
     */
    public List<DeviceInfo> getAllDevices() {
        return mergeDeviceLists();
    }

    /**
     * 获取全部设备（创建时间倒序，其次按 id），用于列表展示与分页，顺序稳定。
     */
    public List<DeviceInfo> getAllDevicesSorted() {
        List<DeviceInfo> list = new ArrayList<>(mergeDeviceLists());
        list.sort(Comparator
                .comparing(DeviceInfo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(d -> d.getId() != null ? d.getId() : ""));
        return list;
    }

    /**
     * 获取最近的设备状态
     */
    public DeviceStatus getLatestStatus(String deviceId) {
        return latestStatus.get(deviceId);
    }

    /**
     * 获取所有最新状态
     */
    public Map<String, DeviceStatus> getAllLatestStatus() {
        return Collections.unmodifiableMap(latestStatus);
    }

    /**
     * 根据设备ID获取设备信息
     */
    public DeviceInfo getDeviceById(String deviceId) {
        DeviceInfo device = localDevices.get(deviceId);
        if (device == null) {
            List<DeviceInfo> redisDevices = redisDeviceService.getAssignedDevices();
            for (DeviceInfo d : redisDevices) {
                if (d.getId().equals(deviceId)) {
                    return d;
                }
            }
        }
        return device;
    }

    /**
     * 根据设备IP获取设备ID
     */
    public String getDeviceIdByIp(String deviceIp) {
        for (DeviceInfo device : localDevices.values()) {
            if (deviceIp.equals(device.getDeviceIp())) {
                return device.getId();
            }
        }
        List<DeviceInfo> redisDevices = redisDeviceService.getAssignedDevices();
        for (DeviceInfo d : redisDevices) {
            if (deviceIp.equals(d.getDeviceIp())) {
                return d.getId();
            }
        }
        return null;
    }

    /**
     * 获取采集统计信息
     */
    public Map<String, Object> getCollectStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("isCollecting", collecting.get());
        stats.put("lastCollectCount", lastCollectCount.get());
        stats.put("lastCollectTime", lastCollectTime > 0 ? new Date(lastCollectTime) : null);
        stats.put("lastCollectDuration", lastCollectDuration + "ms");
        stats.put("lastMetricsCollectTime", lastMetricsCollectTime > 0 ? new Date(lastMetricsCollectTime) : null);
        stats.put("lastMetricsCollectDuration", lastMetricsCollectDuration + "ms");
        stats.put("splitScheduleEnabled", collectorConfig.isSplitScheduleEnabled());
        stats.put("localDeviceCount", localDevices.size());
        stats.put("cachedStatusCount", latestStatus.size());
        stats.put("nodeKey", redisDeviceService.getNodeKey());
        return stats;
    }

    private List<DeviceInfo> mergeDeviceLists() {
        Map<String, DeviceInfo> merged = new LinkedHashMap<>();

        // Redis设备
        try {
            List<DeviceInfo> redisDevices = redisDeviceService.getAssignedDevices();
            for (DeviceInfo d : redisDevices) {
                merged.put(d.getId(), d);
            }
        } catch (Exception e) {
            log.warn("从Redis获取设备列表失败，仅使用本地设备: {}", e.getMessage());
        }

        // 本地设备
        merged.putAll(localDevices);

        return new ArrayList<>(merged.values());
    }
}
