package com.qasky.qdns.controller;

import com.qasky.qdns.service.RedisDeviceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点信息接口
 */
@RestController
@RequestMapping("/api/node")
public class NodeInfoController {

    private final RedisDeviceService redisDeviceService;

    @Value("${spring.application.name:vpn-collector}")
    private String appName;

    @Value("${server.port:18023}")
    private int serverPort;

    public NodeInfoController(RedisDeviceService redisDeviceService) {
        this.redisDeviceService = redisDeviceService;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getNodeInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        try {
            InetAddress addr = InetAddress.getLocalHost();
            info.put("hostname", addr.getHostName());
            info.put("ip", addr.getHostAddress());
        } catch (Exception e) {
            info.put("hostname", "unknown");
            info.put("ip", "127.0.0.1");
        }

        info.put("appName", appName);
        info.put("port", serverPort);
        info.put("nodeKey", redisDeviceService.getNodeKey());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("uptime", runtime.getUptime() + "ms");
        info.put("startTime", new Date(runtime.getStartTime()));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("maxMemory", Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
        info.put("freeMemory", Runtime.getRuntime().freeMemory() / 1024 / 1024 + "MB");
        info.put("totalMemory", Runtime.getRuntime().totalMemory() / 1024 / 1024 + "MB");

        return ResponseEntity.ok(info);
    }
}
