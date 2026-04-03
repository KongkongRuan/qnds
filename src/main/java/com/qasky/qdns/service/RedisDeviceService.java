package com.qasky.qdns.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.DeviceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis设备服务 - 读取分配给本节点的设备列表，写入采集状态
 */
@Service
public class RedisDeviceService {

    private static final Logger log = LoggerFactory.getLogger(RedisDeviceService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${server.port:18023}")
    private int serverPort;

    @Value("${qdns.node.address:}")
    private String nodeAddress;

    @Value("${redis-key.device-list-prefix:qdns:devices:}")
    private String deviceListPrefix;

    @Value("${redis-key.device-status-prefix:qdns:status:}")
    private String deviceStatusPrefix;

    @Value("${redis-key.device-status-ttl:300}")
    private int deviceStatusTtl;

    @Value("${redis-key.trap-configured-prefix:qdns:trap-configured:}")
    private String trapConfiguredPrefix;

    public RedisDeviceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取本节点的Redis Key (ip+端口)
     * 优先使用配置的 qdns.node.address，未配置时自动获取本机IP
     */
    public String getNodeKey() {
        try {
            String ip;
            if (nodeAddress != null && !nodeAddress.isEmpty()) {
                ip = nodeAddress;
                log.debug("使用配置的节点地址: {}", ip);
            } else {
                ip = InetAddress.getLocalHost().getHostAddress();
                log.debug("使用自动获取的本机地址: {}", ip);
            }
            return ip + ":" + serverPort;
        } catch (Exception e) {
            log.error("获取本机IP失败", e);
            return "127.0.0.1:" + serverPort;
        }
    }

    /**
     * 从Redis获取分配给本节点的设备列表
     */
    public List<DeviceInfo> getAssignedDevices() {
        String key = deviceListPrefix + getNodeKey();
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                log.debug("Redis中未找到本节点的设备列表 key={}", key);
                return Collections.emptyList();
            }
            List<DeviceInfo> devices = objectMapper.readValue(json, new TypeReference<List<DeviceInfo>>() {});
            log.info("从Redis获取到 {} 台设备 key={}", devices.size(), key);
            return devices;
        } catch (Exception e) {
            log.error("从Redis读取设备列表失败 key={}", key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 保存设备状态到Redis
     */
    public void saveDeviceStatus(DeviceStatus status) {
        String key = deviceStatusPrefix + status.getDeviceId();
        try {
            String json = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(key, json, deviceStatusTtl, TimeUnit.SECONDS);
            log.debug("设备状态已保存到Redis: {}", key);
        } catch (Exception e) {
            log.error("保存设备状态到Redis失败: {}", key, e);
        }
    }

    /**
     * 批量保存设备状态
     */
    public void batchSaveDeviceStatus(List<DeviceStatus> statusList) {
        for (DeviceStatus status : statusList) {
            saveDeviceStatus(status);
        }
    }

    /**
     * 从Redis读取设备状态
     */
    public DeviceStatus getDeviceStatus(String deviceId) {
        String key = deviceStatusPrefix + deviceId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return objectMapper.readValue(json, DeviceStatus.class);
        } catch (Exception e) {
            log.error("从Redis读取设备状态失败: {}", key, e);
            return null;
        }
    }

    /**
     * 获取所有已保存的设备状态
     */
    public List<DeviceStatus> getAllDeviceStatus() {
        List<DeviceStatus> result = new ArrayList<>();
        Set<String> keys = redisTemplate.keys(deviceStatusPrefix + "*");
        if (keys == null) return result;

        for (String key : keys) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null && !json.isEmpty()) {
                    result.add(objectMapper.readValue(json, DeviceStatus.class));
                }
            } catch (Exception e) {
                log.error("反序列化设备状态失败: {}", key, e);
            }
        }
        return result;
    }

    /**
     * 手动注册设备列表到Redis（用于测试）
     */
    public void registerDevices(List<DeviceInfo> devices) {
        String key = deviceListPrefix + getNodeKey();
        try {
            String json = objectMapper.writeValueAsString(devices);
            redisTemplate.opsForValue().set(key, json);
            log.info("已注册 {} 台设备到Redis key={}", devices.size(), key);
        } catch (Exception e) {
            log.error("注册设备列表到Redis失败", e);
        }
    }

    /**
     * 清除本节点的设备列表
     */
    public void clearDevices() {
        String key = deviceListPrefix + getNodeKey();
        redisTemplate.delete(key);
        log.info("已清除本节点设备列表 key={}", key);
    }

    /**
     * 保存更新后的设备信息（带采集数据）回写到设备列表
     */
    public void updateDeviceInfo(DeviceInfo device, DeviceStatus status) {
        if (status == null || !status.isOnline()) return;
        device.setStatus(status.isOnline() ? "online" : "offline");
        device.setCpuUsed(String.valueOf(status.getCpuUsage()));
        device.setMemUsed(String.valueOf(status.getMemUsagePercent()));
        device.setDiskUsed(String.valueOf(status.getDiskPercent()));
        device.setPasswordCardUseCount(String.valueOf(status.getCryptoCardCallCount()));
        device.setAlgorithmIdentification(status.getCryptoCardAlgorithms());
        device.setVersion(status.getVpnFirmwareVersion());
        device.setUpdateTime(new Date());
    }

    /**
     * 获取已配置Trap的设备ID集合
     */
    public Set<String> getTrapConfiguredDeviceIds() {
        String key = trapConfiguredPrefix + getNodeKey();
        try {
            Set<String> ids = redisTemplate.opsForSet().members(key);
            return ids != null ? ids : Collections.emptySet();
        } catch (Exception e) {
            log.error("读取Trap已配置集合失败 key={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 批量添加已配置Trap的设备ID
     */
    public void addTrapConfiguredDeviceIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        String key = trapConfiguredPrefix + getNodeKey();
        try {
            redisTemplate.opsForSet().add(key, ids.toArray(new String[0]));
        } catch (Exception e) {
            log.error("写入Trap已配置集合失败 key={}", key, e);
        }
    }

    /**
     * 批量移除已配置Trap的设备ID
     */
    public void removeTrapConfiguredDeviceIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        String key = trapConfiguredPrefix + getNodeKey();
        try {
            redisTemplate.opsForSet().remove(key, ids.toArray(new Object[0]));
        } catch (Exception e) {
            log.error("移除Trap已配置集合失败 key={}", key, e);
        }
    }
}
