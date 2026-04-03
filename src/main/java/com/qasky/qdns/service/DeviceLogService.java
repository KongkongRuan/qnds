package com.qasky.qdns.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.DeviceLogBatchResult;
import com.qasky.qdns.model.dto.DeviceLogEntry;
import com.qasky.qdns.model.dto.DeviceLogFetchResult;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DeviceLogService {

    private static final Logger log = LoggerFactory.getLogger(DeviceLogService.class);

    @Value("${vpn-sim.api.port:8888}")
    private int vpnSimApiPort;

    @Value("${vpn-sim.api.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${vpn-sim.api.timeout.read:10000}")
    private int readTimeout;

    @Value("${device-log.ssh.enabled:true}")
    private boolean sshFallbackEnabled;

    @Value("${device-log.ssh.timeout.connect:10000}")
    private int sshConnectTimeout;

    @Value("${device-log.ssh.timeout.execute:30000}")
    private int sshExecuteTimeout;

    private final DeviceCollectorService deviceCollectorService;
    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    private final ExecutorService executorService;

    public DeviceLogService(DeviceCollectorService deviceCollectorService) {
        this.deviceCollectorService = deviceCollectorService;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(10, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "device-log-fetcher-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    public DeviceLogFetchResult fetchDeviceLogs(String deviceId, Integer count, String since) {
        DeviceInfo device = deviceCollectorService.getDeviceById(deviceId);
        if (device == null) {
            DeviceLogFetchResult result = new DeviceLogFetchResult();
            result.setDeviceId(deviceId);
            result.setSuccess(false);
            result.setErrorMessage("设备不存在: " + deviceId);
            return result;
        }
        return fetchDeviceLogs(device, count, since);
    }

    public DeviceLogFetchResult fetchDeviceLogs(DeviceInfo device, Integer count, String since) {
        long startTime = System.currentTimeMillis();
        DeviceLogFetchResult result = new DeviceLogFetchResult();
        result.setDeviceId(device.getId());
        result.setDeviceIp(device.getDeviceIp());
        result.setDevicePort(device.getDevicePort());

        int logCount = count != null && count > 0 ? Math.min(count, 500) : 50;

        DeviceLogFetchResult httpResult = fetchViaHttp(device, logCount, since);
        if (httpResult.getSuccess()) {
            httpResult.setFetchTime(System.currentTimeMillis() - startTime);
            return httpResult;
        }

        if (sshFallbackEnabled) {
            log.info("HTTP拉取日志失败，尝试SSH回退: deviceId={}, error={}", device.getId(), httpResult.getErrorMessage());
            DeviceLogFetchResult sshResult = fetchViaSsh(device, logCount, since);
            if (sshResult.getSuccess()) {
                sshResult.setFetchTime(System.currentTimeMillis() - startTime);
                return sshResult;
            }
            result.setErrorMessage("HTTP和SSH均失败: HTTP=" + httpResult.getErrorMessage() + "; SSH=" + sshResult.getErrorMessage());
        } else {
            result.setErrorMessage(httpResult.getErrorMessage());
        }

        result.setSuccess(false);
        result.setFetchMethod("FAILED");
        result.setFetchTime(System.currentTimeMillis() - startTime);
        return result;
    }

    private DeviceLogFetchResult fetchViaHttp(DeviceInfo device, int count, String since) {
        DeviceLogFetchResult result = new DeviceLogFetchResult();
        result.setDeviceId(device.getId());
        result.setDeviceIp(device.getDeviceIp());
        result.setDevicePort(device.getDevicePort());

        try {
            String url = String.format("http://%s:%d/api/devices/by-address/%s/%s/logs?count=%d",
                    device.getDeviceIp(), vpnSimApiPort, device.getDeviceIp(), device.getDevicePort(), count);
            if (since != null && !since.trim().isEmpty()) {
                url += "&since=" + since.trim();
            }

            log.debug("通过HTTP拉取日志: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = getRestTemplate().exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<DeviceLogEntry> logs = objectMapper.readValue(response.getBody(),
                        new TypeReference<List<DeviceLogEntry>>() {});
                result.setSuccess(true);
                result.setFetchMethod("HTTP");
                result.setLogs(logs);
                result.setLogCount(logs != null ? logs.size() : 0);
                log.info("HTTP拉取日志成功: deviceId={}, count={}", device.getId(), result.getLogCount());
            } else {
                result.setSuccess(false);
                result.setErrorMessage("HTTP响应异常: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.warn("HTTP拉取日志失败: deviceId={}, error={}", device.getId(), e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage("HTTP连接失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("HTTP拉取日志异常: deviceId={}", device.getId(), e);
            result.setSuccess(false);
            result.setErrorMessage("HTTP处理异常: " + e.getMessage());
        }

        return result;
    }

    private DeviceLogFetchResult fetchViaSsh(DeviceInfo device, int count, String since) {
        DeviceLogFetchResult result = new DeviceLogFetchResult();
        result.setDeviceId(device.getId());
        result.setDeviceIp(device.getDeviceIp());
        result.setDevicePort(device.getDevicePort());

        Integer sshPort = device.getSshPort() != null ? device.getSshPort() : 22;
        String sshUsername = device.getSshUsername();
        String sshPassword = device.getSshPassword();

        if (sshUsername == null || sshUsername.isEmpty()) {
            sshUsername = "admin";
        }
        if (sshPassword == null || sshPassword.isEmpty()) {
            sshPassword = "admin";
        }

        SSHClient ssh = null;
        try {
            ssh = new SSHClient();
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.setConnectTimeout(sshConnectTimeout);
            ssh.setTimeout(sshExecuteTimeout);

            log.debug("通过SSH连接设备: {}:{}", device.getDeviceIp(), sshPort);
            ssh.connect(device.getDeviceIp(), sshPort);
            ssh.authPassword(sshUsername, sshPassword);

            String command = "show log " + count;
            if (since != null && !since.trim().isEmpty()) {
                command = "show log since " + since.trim();
            }

            log.debug("通过SSH执行命令: deviceId={}, command={}", device.getId(), command);

            Session session = ssh.startSession();
            Session.Command cmd = session.exec(command);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = cmd.getInputStream().read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            cmd.join();
            session.close();

            String output = baos.toString("UTF-8");

            if (output != null && !output.trim().isEmpty()) {
                List<DeviceLogEntry> logs = parseSshLogOutput(output);
                result.setSuccess(true);
                result.setFetchMethod("SSH");
                result.setLogs(logs);
                result.setLogCount(logs.size());
                log.info("SSH拉取日志成功: deviceId={}, count={}", device.getId(), logs.size());
            } else {
                result.setSuccess(false);
                result.setErrorMessage("SSH命令无输出");
            }
        } catch (Exception e) {
            log.warn("SSH拉取日志失败: deviceId={}, error={}", device.getId(), e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage("SSH连接失败: " + e.getMessage());
        } finally {
            if (ssh != null && ssh.isConnected()) {
                try {
                    ssh.disconnect();
                } catch (Exception ignored) {}
            }
        }

        return result;
    }

    private List<DeviceLogEntry> parseSshLogOutput(String output) {
        List<DeviceLogEntry> logs = new ArrayList<>();
        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            DeviceLogEntry entry = parseLogLine(line);
            if (entry != null) {
                logs.add(entry);
            }
        }
        return logs;
    }

    private DeviceLogEntry parseLogLine(String line) {
        try {
            if (line.startsWith("[") && line.contains("]")) {
                DeviceLogEntry entry = new DeviceLogEntry();
                int firstBracket = line.indexOf(']');
                if (firstBracket > 0) {
                    entry.setTimestamp(line.substring(1, firstBracket));
                    String rest = line.substring(firstBracket + 1).trim();

                    if (rest.startsWith("[") && rest.contains("]")) {
                        int secondBracket = rest.indexOf(']');
                        entry.setLevel(rest.substring(1, secondBracket));
                        rest = rest.substring(secondBracket + 1).trim();

                        if (rest.startsWith("[") && rest.contains("]")) {
                            int thirdBracket = rest.indexOf(']');
                            entry.setModule(rest.substring(1, thirdBracket));
                            rest = rest.substring(thirdBracket + 1).trim();
                        }
                    }

                    entry.setMessage(rest);
                    return entry;
                }
            }
        } catch (Exception e) {
            log.debug("解析日志行失败: {}", line);
        }
        return null;
    }

    public DeviceLogBatchResult fetchBatchLogs(List<String> deviceIds, Integer count, String since) {
        long startTime = System.currentTimeMillis();
        DeviceLogBatchResult batchResult = new DeviceLogBatchResult();
        batchResult.setTotalDevices(deviceIds.size());

        List<DeviceLogFetchResult> results = new ArrayList<>();
        List<Future<DeviceLogFetchResult>> futures = new ArrayList<>();

        for (String deviceId : deviceIds) {
            futures.add(executorService.submit(() -> fetchDeviceLogs(deviceId, count, since)));
        }

        int successCount = 0;
        int failCount = 0;

        for (Future<DeviceLogFetchResult> future : futures) {
            try {
                DeviceLogFetchResult result = future.get(60, TimeUnit.SECONDS);
                results.add(result);
                if (Boolean.TRUE.equals(result.getSuccess())) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (TimeoutException e) {
                DeviceLogFetchResult timeoutResult = new DeviceLogFetchResult();
                timeoutResult.setSuccess(false);
                timeoutResult.setErrorMessage("拉取超时");
                timeoutResult.setFetchMethod("FAILED");
                results.add(timeoutResult);
                failCount++;
            } catch (Exception e) {
                DeviceLogFetchResult errorResult = new DeviceLogFetchResult();
                errorResult.setSuccess(false);
                errorResult.setErrorMessage("拉取异常: " + e.getMessage());
                errorResult.setFetchMethod("FAILED");
                results.add(errorResult);
                failCount++;
            }
        }

        batchResult.setSuccessCount(successCount);
        batchResult.setFailCount(failCount);
        batchResult.setResults(results);
        batchResult.setTotalTime(System.currentTimeMillis() - startTime);

        log.info("批量拉取日志完成: total={}, success={}, fail={}, time={}ms",
                batchResult.getTotalDevices(), successCount, failCount, batchResult.getTotalTime());

        return batchResult;
    }

    public DeviceLogBatchResult fetchAllDeviceLogs(Integer count, String since) {
        List<DeviceInfo> devices = deviceCollectorService.getAllDevices();
        List<String> deviceIds = new ArrayList<>();
        for (DeviceInfo device : devices) {
            if (device.getId() != null) {
                deviceIds.add(device.getId());
            }
        }
        return fetchBatchLogs(deviceIds, count, since);
    }
}
