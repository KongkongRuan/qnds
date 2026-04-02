package com.qasky.qdns.snmp;

import com.qasky.qdns.model.AlarmInfo;
import com.qasky.qdns.service.AlarmForwardService;
import com.qasky.qdns.service.DeviceCollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * SNMP Trap接收器
 */
@Component
public class SnmpTrapReceiver implements CommandResponder {

    private static final Logger log = LoggerFactory.getLogger(SnmpTrapReceiver.class);

    @Value("${snmp.trap.enabled:true}")
    private boolean trapEnabled;

    @Value("${snmp.trap.listen-port:1162}")
    private int listenPort;

    @Autowired
    private AlarmForwardService alarmForwardService;

    @Autowired
    private DeviceCollectorService deviceCollectorService;

    private Snmp snmp;
    private TransportMapping<?> transport;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String ENTERPRISE_PREFIX = "1.3.6.1.4.1.99999";

    private static final Map<String, String> TRAP_TYPE_MAP = new HashMap<>();
    static {
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.1", "DEVICE_DOWN");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.2", "DEVICE_UP");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.3", "CPU_HIGH");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.4", "MEMORY_HIGH");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.5", "DISK_HIGH");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.6", "TUNNEL_DOWN");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.7", "TUNNEL_UP");
        TRAP_TYPE_MAP.put(ENTERPRISE_PREFIX + ".0.8", "CRYPTO_ERROR");
    }

    @PostConstruct
    public void init() throws IOException {
        if (!trapEnabled) {
            log.info("SNMP Trap接收器已禁用");
            return;
        }

        Address listenAddress = GenericAddress.parse("udp:0.0.0.0/" + listenPort);
        transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);

        snmp = new Snmp(transport);
        snmp.addCommandResponder(this);

        transport.listen();
        log.info("SNMP Trap接收器已启动，监听端口: {}", listenPort);
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (snmp != null) {
            snmp.close();
            log.info("SNMP Trap接收器已关闭");
        }
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        PDU pdu = event.getPDU();
        if (pdu == null) {
            return;
        }

        Address peerAddress = event.getPeerAddress();
        String deviceIp = extractDeviceIp(peerAddress);

        log.info("收到Trap，来源: {}, 类型: {}", deviceIp, pdu.getType());

        AlarmInfo alarm = parseTrap(deviceIp, pdu);

        if (alarm != null && alarmForwardService != null) {
            alarmForwardService.forwardAlarm(alarm);
        }
    }

    private String extractDeviceIp(Address address) {
        if (address instanceof UdpAddress) {
            return ((UdpAddress) address).getInetAddress().getHostAddress();
        }
        return address.toString();
    }

    private AlarmInfo parseTrap(String deviceIp, PDU pdu) {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setDeviceIp(deviceIp);
        alarm.setAlarmCreateDate(LocalDateTime.now().format(DATE_FORMATTER));

        try {
            alarm.setNodeKey(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            alarm.setNodeKey("unknown");
        }

        // 从 Trap OID 中获取信息
        String alarmSourceFromOid = null;
        String deviceIpFromOid = null;
        String trapOid = null;
        String currentValue = "";
        String severity = "2";

        for (VariableBinding vb : pdu.getVariableBindings()) {
            String oid = vb.getOid().toString();
            String value = vb.getVariable().toString();

            if (oid.startsWith(ENTERPRISE_PREFIX + ".0.")) {
                trapOid = oid;
            } else if (oid.equals(ENTERPRISE_PREFIX + ".9.1.0")) {
                severity = value;
            } else if (oid.equals(ENTERPRISE_PREFIX + ".9.2.0")) {
                // Trap 中携带的真实设备ID（VPN-Sim 发送的是 device_id）
                alarmSourceFromOid = value;
            } else if (oid.equals(ENTERPRISE_PREFIX + ".9.3.0")) {
                // Trap 中携带的真实设备IP
                deviceIpFromOid = value;
                alarm.setDeviceIp(value);
            } else if (oid.equals(ENTERPRISE_PREFIX + ".9.5.0")) {
                currentValue = value;
            } else if (oid.equals(ENTERPRISE_PREFIX + ".9.8.0")) {
                alarm.setAlarmData(value);
            }
        }

        // 设置 alarmSource：
        // 1. 优先用 OID 中的设备IP去Redis查询deviceId
        // 2. 其次用Trap来源IP去Redis查询deviceId
        // 3. 最后使用 OID 中的设备ID
        String deviceId = null;
        
        // 先用OID中的设备IP查询
        if (deviceIpFromOid != null && !deviceIpFromOid.isEmpty()) {
            deviceId = deviceCollectorService.getDeviceIdByIp(deviceIpFromOid);
        }
        
        // 再用Trap来源IP查询
        if (deviceId == null) {
            deviceId = deviceCollectorService.getDeviceIdByIp(deviceIp);
        }
        
        // 如果Redis中都查不到，使用OID中的设备ID
        if (deviceId != null) {
            alarm.setAlarmSource(deviceId);
        } else if (alarmSourceFromOid != null && !alarmSourceFromOid.isEmpty()) {
            alarm.setAlarmSource(alarmSourceFromOid);
        } else {
            alarm.setAlarmSource(deviceIp);
        }

        if (trapOid != null) {
            alarm.setTrapOid(trapOid);
            String alarmCode = TRAP_TYPE_MAP.get(trapOid);
            if (alarmCode != null) {
                alarm.setAlarmCode(alarmCode);
            } else {
                alarm.setAlarmCode("UNKNOWN");
            }
        }

        if (alarm.getAlarmData() == null || alarm.getAlarmData().isEmpty()) {
            alarm.setAlarmData(currentValue);
        }

        try {
            alarm.setAlarmLevel(Integer.parseInt(severity));
        } catch (NumberFormatException e) {
            alarm.setAlarmLevel(2);
        }

        return alarm;
    }

    public int getListenPort() {
        return listenPort;
    }

    public boolean isRunning() {
        return snmp != null && transport != null && transport.isListening();
    }
}
