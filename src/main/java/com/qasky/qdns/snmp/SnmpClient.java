package com.qasky.qdns.snmp;

import com.qasky.qdns.config.SnmpConfig;
import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.SnmpResult;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.security.Security;
import java.util.*;

@Component
public class SnmpClient {

    private static final Logger log = LoggerFactory.getLogger(SnmpClient.class);

    private final SnmpConfig snmpConfig;
    private Snmp snmp;
    private USM usm;

    public SnmpClient(SnmpConfig snmpConfig) {
        this.snmpConfig = snmpConfig;
    }

    @PostConstruct
    public void init() throws IOException {
        log.info("开始初始化 SNMP 客户端...");

        // 1. 全局注册 BouncyCastle 安全提供者 (为后续 SM3/SM4 底层加密计算提供支持)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle (BC) 安全提供程序已全局注册");
        }

        // 2. 绕过 SPI，优雅、显式地初始化 SNMP4J 协议注册表
        initSecurityProtocols();

        // 3. 原有的 SNMP4J 引擎初始化
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);

        USM usmInstance = new USM(SecurityProtocols.getInstance(),
                new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usmInstance);
        usm = usmInstance;

        log.info("USM 已创建并添加到 SecurityModels");

        transport.listen();
        log.info("SNMP客户端初始化完成");
    }

    /**
     * 集中管理所有 SNMPv3 安全协议的注册
     * 完美避开 Spring Boot 打包引发的 SPI 扫描失效问题。
     * 后续增加国密支持时，只需在此处添加自定义的 new AuthSM3() 和 new PrivSM4() 即可。
     */
    private void initSecurityProtocols() {
        SecurityProtocols sp = SecurityProtocols.getInstance();

        // 注册标准认证协议及国密 SM3
        AuthenticationProtocol[] authProtos = {
                new AuthMD5(), new AuthSHA(),
                new AuthHMAC128SHA224(), new AuthHMAC192SHA256(),
                new AuthHMAC256SHA384(), new AuthHMAC384SHA512(),
                new AuthSM3(snmpConfig.getSm().isLegacyMode()) // 新增 SM3 认证协议
        };
        for (AuthenticationProtocol p : authProtos) {
            sp.addAuthenticationProtocol(p);
        }

        // 注册标准加密协议及国密 SM4
        PrivacyProtocol[] privProtos = {
                new PrivDES(), new PrivAES128(), new PrivAES192(), new PrivAES256(),
                new PrivSM4(snmpConfig.getSm().isLegacyMode()) // 新增 SM4 加密协议
        };
        for (PrivacyProtocol p : privProtos) {
            sp.addPrivacyProtocol(p);
        }

        log.info("SNMP4J 安全协议显式注册完成，已成功挂载国密(SM3/SM4)扩展");
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (snmp != null) {
            snmp.close();
            log.info("SNMP客户端已关闭");
        }
    }

    /**
     * SNMP GET请求（支持v2c和v3），v3 凭据取自全局配置
     */
    public List<SnmpResult> get(String host, int port, List<String> oids, String protocol) {
        return get(host, port, oids, protocol, null, null);
    }

    public List<SnmpResult> get(String host, int port, List<String> oids, String protocol, DeviceInfo v3Device) {
        return get(host, port, oids, protocol, v3Device, null);
    }

    public List<SnmpResult> get(String host, int port, List<String> oids, String protocol, DeviceInfo v3Device, String community) {
        List<SnmpResult> results = new ArrayList<>();
        try {
            Target<?> target = createTarget(host, port, protocol, v3Device, community);
            PDU pdu = createPDU(protocol);

            for (String oid : oids) {
                pdu.add(new VariableBinding(new OID(oid)));
            }

            log.info("发送 SNMP 请求: target={}, pdu type={}, oid count={}",
                    target.getAddress(), pdu.getType(), pdu.size());

            long startTime = System.currentTimeMillis();
            ResponseEvent<?> event = snmp.send(pdu, target);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("SNMP 响应: elapsed={}ms, event={}, response={}",
                    elapsed, event != null, event != null ? event.getResponse() : null);

            if (event != null && event.getError() != null) {
                log.error("SNMP 请求错误: {}", event.getError().getMessage());
            }

            if (event == null || event.getResponse() == null) {
                for (String oid : oids) {
                    results.add(SnmpResult.error(oid, "Timeout - no response"));
                }
                return results;
            }

            PDU response = event.getResponse();
            log.info("SNMP 响应 PDU: errorStatus={}, errorIndex={}",
                    response.getErrorStatus(), response.getErrorIndex());

            if (response.getErrorStatus() != PDU.noError) {
                String errMsg = response.getErrorStatusText();
                for (String oid : oids) {
                    results.add(SnmpResult.error(oid, errMsg));
                }
                return results;
            }

            for (VariableBinding vb : response.getVariableBindings()) {
                Variable var = vb.getVariable();
                if (var instanceof Null || var.toString().contains("noSuchObject")
                        || var.toString().contains("noSuchInstance")
                        || var.toString().contains("endOfMibView")) {
                    results.add(SnmpResult.error(vb.getOid().toString(), "noSuchObject"));
                } else {
                    results.add(SnmpResult.success(
                            vb.getOid().toString(),
                            var.toString(),
                            var.getSyntaxString()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("SNMP GET失败 {}:{} - 发生了底层异常:", host, port, e);
            for (String oid : oids) {
                results.add(SnmpResult.error(oid, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * SNMP GET请求（单个OID）
     */
    public SnmpResult get(String host, int port, String oid, String protocol) {
        return get(host, port, oid, protocol, null, null);
    }

    public SnmpResult get(String host, int port, String oid, String protocol, DeviceInfo v3Device) {
        return get(host, port, oid, protocol, v3Device, null);
    }

    public SnmpResult get(String host, int port, String oid, String protocol, DeviceInfo v3Device, String community) {
        List<SnmpResult> results = get(host, port, Collections.singletonList(oid), protocol, v3Device, community);
        return results.isEmpty() ? SnmpResult.error(oid, "Empty response") : results.get(0);
    }

    /**
     * SNMPv2c GET，可指定 community 与超时（用于网段发现等场景）
     */
    public List<SnmpResult> getV2c(String host, int port, List<String> oids, String community,
                                   int timeoutMs, int retries) {
        List<SnmpResult> results = new ArrayList<>();
        String comm = (community != null && !community.isEmpty())
                ? community
                : snmpConfig.getV2c().getCommunityRead();
        try {
            Address address = GenericAddress.parse("udp:" + host + "/" + port);
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setAddress(address);
            target.setCommunity(new OctetString(comm));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(timeoutMs);
            target.setRetries(retries);

            PDU pdu = new PDU();
            pdu.setType(PDU.GET);
            for (String oid : oids) {
                pdu.add(new VariableBinding(new OID(oid)));
            }

            ResponseEvent<?> event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                for (String oid : oids) {
                    results.add(SnmpResult.error(oid, "Timeout - no response"));
                }
                return results;
            }

            PDU response = event.getResponse();
            if (response.getErrorStatus() != PDU.noError) {
                String errMsg = response.getErrorStatusText();
                for (String oid : oids) {
                    results.add(SnmpResult.error(oid, errMsg));
                }
                return results;
            }

            for (VariableBinding vb : response.getVariableBindings()) {
                Variable var = vb.getVariable();
                if (var instanceof Null || var.toString().contains("noSuchObject")
                        || var.toString().contains("noSuchInstance")
                        || var.toString().contains("endOfMibView")) {
                    results.add(SnmpResult.error(vb.getOid().toString(), "noSuchObject"));
                } else {
                    results.add(SnmpResult.success(
                            vb.getOid().toString(),
                            var.toString(),
                            var.getSyntaxString()
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("SNMPv2c GET失败 {}:{} - {}", host, port, e.getMessage());
            for (String oid : oids) {
                results.add(SnmpResult.error(oid, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * SNMP WALK（GETBULK/GETNEXT遍历）
     */
    public List<SnmpResult> walk(String host, int port, String rootOid, String protocol) {
        return walk(host, port, rootOid, protocol, null, null);
    }

    public List<SnmpResult> walk(String host, int port, String rootOid, String protocol, DeviceInfo v3Device) {
        return walk(host, port, rootOid, protocol, v3Device, null);
    }

    public List<SnmpResult> walk(String host, int port, String rootOid, String protocol, DeviceInfo v3Device, String community) {
        List<SnmpResult> results = new ArrayList<>();
        try {
            Target<?> target = createTarget(host, port, protocol, v3Device, community);
            TableUtils tableUtils = new TableUtils(snmp, new DefaultPDUFactory(
                    "SNMPv3".equalsIgnoreCase(protocol) ? PDU.GETBULK : PDU.GETNEXT
            ));
            tableUtils.setMaxNumRowsPerPDU(snmpConfig.getMaxRepetitions());

            OID tableOid = new OID(rootOid);
            List<TableEvent> events = tableUtils.getTable(target,
                    new OID[]{tableOid}, null, null);

            for (TableEvent event : events) {
                if (event.isError()) {
                    log.debug("Table walk error for {}: {}", rootOid, event.getErrorMessage());
                    continue;
                }
                for (VariableBinding vb : event.getColumns()) {
                    if (vb != null) {
                        Variable var = vb.getVariable();
                        if (!(var instanceof Null)) {
                            results.add(SnmpResult.success(
                                    vb.getOid().toString(),
                                    var.toString(),
                                    var.getSyntaxString()
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("SNMP WALK失败 {}:{} rootOid={} - 发生了底层异常:", host, port, rootOid, e);
        }
        return results;
    }

    /**
     * SNMP GETNEXT请求
     */
    public List<SnmpResult> getNext(String host, int port, List<String> oids, String protocol) {
        return getNext(host, port, oids, protocol, null);
    }

    public List<SnmpResult> getNext(String host, int port, List<String> oids, String protocol, DeviceInfo v3Device) {
        List<SnmpResult> results = new ArrayList<>();
        try {
            Target<?> target = createTarget(host, port, protocol, v3Device);
            PDU pdu = createPDU(protocol);
            pdu.setType(PDU.GETNEXT);

            for (String oid : oids) {
                pdu.add(new VariableBinding(new OID(oid)));
            }

            ResponseEvent<?> event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                for (String oid : oids) {
                    results.add(SnmpResult.error(oid, "Timeout"));
                }
                return results;
            }

            for (VariableBinding vb : event.getResponse().getVariableBindings()) {
                Variable var = vb.getVariable();
                if (var instanceof Null || var.toString().contains("endOfMibView")) {
                    results.add(SnmpResult.error(vb.getOid().toString(), "endOfMibView"));
                } else {
                    results.add(SnmpResult.success(
                            vb.getOid().toString(),
                            var.toString(),
                            var.getSyntaxString()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("SNMP GETNEXT失败 {}:{} - 发生了底层异常:", host, port, e);
            for (String oid : oids) {
                results.add(SnmpResult.error(oid, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 批量GET，自动分批（每批最多maxPerBatch个OID）
     */
    public List<SnmpResult> batchGet(String host, int port, List<String> oids, String protocol, int maxPerBatch) {
        return batchGet(host, port, oids, protocol, maxPerBatch, null);
    }

    public List<SnmpResult> batchGet(String host, int port, List<String> oids, String protocol,
                                     int maxPerBatch, DeviceInfo v3Device) {
        List<SnmpResult> allResults = new ArrayList<>();
        for (int i = 0; i < oids.size(); i += maxPerBatch) {
            int end = Math.min(i + maxPerBatch, oids.size());
            List<String> batch = oids.subList(i, end);
            allResults.addAll(get(host, port, batch, protocol, v3Device));
        }
        return allResults;
    }

    /**
     * 检测设备是否在线（通过sysDescr OID探测）
     */
    public boolean isDeviceOnline(String host, int port, String protocol) {
        return isDeviceOnline(host, port, protocol, null);
    }

    public boolean isDeviceOnline(String host, int port, String protocol, DeviceInfo v3Device) {
        SnmpResult result = get(host, port, "1.3.6.1.2.1.1.1.0", protocol, v3Device);
        return result.isSuccess();
    }

    private Target<?> createTarget(String host, int port, String protocol, DeviceInfo v3Device) {
        return createTarget(host, port, protocol, v3Device, null);
    }

    private Target<?> createTarget(String host, int port, String protocol, DeviceInfo v3Device, String community) {
        Address address = GenericAddress.parse("udp:" + host + "/" + port);
        if ("SNMPv3".equalsIgnoreCase(protocol)) {
            return createV3Target(address, v3Device);
        } else {
            return createV2Target(address, community);
        }
    }

    private static String v3TextOrConfig(String fromDevice, String fromConfig) {
        if (fromDevice != null && !fromDevice.trim().isEmpty()) {
            return fromDevice.trim();
        }
        return fromConfig;
    }

    private CommunityTarget<Address> createV2Target(Address address, String community) {
        CommunityTarget<Address> target = new CommunityTarget<>();
        target.setAddress(address);
        String comm = (community != null && !community.trim().isEmpty())
                ? community.trim()
                : snmpConfig.getV2c().getCommunityRead();
        target.setCommunity(new OctetString(comm));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(snmpConfig.getTimeout());
        target.setRetries(snmpConfig.getRetries());
        return target;
    }

    private UserTarget<Address> createV3Target(Address address, DeviceInfo v3Device) {
        SnmpConfig.V3Config v3 = snmpConfig.getV3();

        // 获取用户名、密码和加密协议配置
        String username = v3TextOrConfig(v3Device != null ? v3Device.getSnmpV3Username() : null, v3.getUsername());
        String authPassword = v3TextOrConfig(v3Device != null ? v3Device.getSnmpV3AuthPassword() : null, v3.getAuthPassword());
        String privPassword = v3TextOrConfig(v3Device != null ? v3Device.getSnmpV3PrivPassword() : null, v3.getPrivPassword());

        String authProtoName = v3TextOrConfig(v3Device != null ? v3Device.getSnmpV3AuthProtocol() : null, v3.getAuthProtocol());
        String privProtoName = v3TextOrConfig(v3Device != null ? v3Device.getSnmpV3PrivProtocol() : null, v3.getPrivProtocol());

        // 防御性处理：只有在密码真实存在时，才创建 OctetString；否则直接给 null
        OctetString authPassOS = (authPassword != null && !authPassword.isEmpty()) ? new OctetString(authPassword) : null;
        OctetString privPassOS = (privPassword != null && !privPassword.isEmpty()) ? new OctetString(privPassword) : null;

        OID actualAuthProtocol = (authPassOS != null) ? getAuthProtocol(authProtoName) : null;
        OID actualPrivProtocol = (privPassOS != null) ? getPrivProtocol(privProtoName) : null;

        log.info("SNMPv3 配置: username={}, authProto={}, authProtoOID={}, privProto={}, privProtoOID={}, 有无Auth密码={}, 有无Priv密码={}",
                username, authProtoName, actualAuthProtocol, privProtoName, actualPrivProtocol,
                (authPassOS != null), (privPassOS != null));

        OctetString securityName = new OctetString(username);

        // 使用安全过滤后的协议和密码构建 UsmUser
        UsmUser user = new UsmUser(
                securityName,
                actualAuthProtocol,
                authPassOS,
                actualPrivProtocol,
                privPassOS
        );

        // 1. 先在全局 USM 中注册泛化用户
        snmp.getUSM().removeAllUsers(securityName);
        snmp.getUSM().addUser(securityName, user);
        log.info("泛化用户已添加到 USM，准备进行 EngineID 发现");

        // 2. 显式触发 Engine ID 发现流程
        long timeoutMs = snmpConfig.getTimeout();
        byte[] engineIdBytes = snmp.discoverAuthoritativeEngineID(address, timeoutMs);

        // 3. 构建 UserTarget 基础属性
        int secLevel = SecurityLevel.NOAUTH_NOPRIV;
        if (authPassword != null && !authPassword.isEmpty()) {
            if (privPassword != null && !privPassword.isEmpty()) {
                secLevel = SecurityLevel.AUTH_PRIV;
            } else {
                secLevel = SecurityLevel.AUTH_NOPRIV;
            }
        }

        UserTarget<Address> target = new UserTarget<>();
        target.setAddress(address);
        target.setVersion(SnmpConstants.version3);
        target.setSecurityLevel(secLevel);
        target.setSecurityName(securityName);
        target.setTimeout(snmpConfig.getTimeout());
        target.setRetries(snmpConfig.getRetries());

        // 4. 关键修复：不仅要设置 Target 的 EngineID，还要将 EngineID 与 USM 里的用户强绑定
        if (engineIdBytes != null) {
            log.info("成功发现远端 EngineID: {}", new OctetString(engineIdBytes).toHexString());

            // 将发现的 EngineID 强行注入到 Target 中
            target.setAuthoritativeEngineID(engineIdBytes);

            // 【核心修复】将用户凭据与这个特定的远端 EngineID 绑定，生成本地化密钥
            snmp.getUSM().addUser(securityName, new OctetString(engineIdBytes), user);
            log.info("已成功将用户凭据与远端 EngineID 绑定到 USM");
        } else {
            log.warn("EngineID 发现失败或超时，将尝试回退到 SNMP4J 默认盲发行为 (目标: {})", address);
        }

        log.info("UserTarget 配置完成: address={}, securityLevel={}, engineID绑定状态={}",
                address, target.getSecurityLevel(), (engineIdBytes != null));

        return target;
    }

    private PDU createPDU(String protocol) {
        if ("SNMPv3".equalsIgnoreCase(protocol)) {
            ScopedPDU pdu = new ScopedPDU();
            pdu.setType(PDU.GET);
            return pdu;
        } else {
            PDU pdu = new PDU();
            pdu.setType(PDU.GET);
            return pdu;
        }
    }

    private OID getAuthProtocol(String name) {
        if (name == null) return AuthSHA.ID;
        switch (name.toUpperCase()) {
            case "MD5":
                return AuthMD5.ID;
            case "HMAC128SHA224":
                return AuthHMAC128SHA224.ID;
            case "HMAC192SHA256":
                return AuthHMAC192SHA256.ID;
            case "SM3":
                return new OID("1.3.6.1.4.1.62068.1.1");
            case "SHA":
            default:
                return AuthSHA.ID;
        }
    }

    private OID getPrivProtocol(String name) {
        if (name == null) return PrivAES128.ID;
        switch (name.toUpperCase()) {
            case "DES":
                return PrivDES.ID;
            case "AES192":
                return PrivAES192.ID;
            case "AES256":
                return PrivAES256.ID;
            case "SM4":
                return new OID("1.3.6.1.4.1.62068.2.1");
            case "AES128":
            case "AES":
            default:
                return PrivAES128.ID;
        }
    }

    /**
     * SNMP SET请求（单个OID）
     * @param host 设备IP
     * @param port SNMP端口
     * @param oid 目标OID
     * @param value 设置值
     * @param type 值类型（INTEGER/STRING/OID/IPADDRESS/COUNTER/GAUGE/TIMETICKS）
     * @param protocol SNMP协议版本
     * @return 操作结果
     */
    public SnmpResult set(String host, int port, String oid, String value, String type, String protocol) {
        return set(host, port, oid, value, type, protocol, null);
    }

    /**
     * SNMP SET请求（支持v3设备凭据覆盖）
     */
    public SnmpResult set(String host, int port, String oid, String value, String type,
                          String protocol, DeviceInfo v3Device) {
        return set(host, port, oid, value, type, protocol, v3Device, null);
    }

    /**
     * SNMP SET请求（支持v3设备凭据覆盖 + v2c自定义community）
     */
    public SnmpResult set(String host, int port, String oid, String value, String type,
                          String protocol, DeviceInfo v3Device, String communityOverride) {
        try {
            Target<?> target = createSetTarget(host, port, protocol, v3Device, communityOverride);
            PDU pdu = createSetPDU(oid, value, type, protocol);

            ResponseEvent<?> event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                return SnmpResult.error(oid, "Timeout - no response");
            }

            PDU response = event.getResponse();
            if (response.getErrorStatus() != PDU.noError) {
                return SnmpResult.error(oid, response.getErrorStatusText());
            }

            VariableBinding vb = response.get(0);
            return SnmpResult.success(vb.getOid().toString(), vb.getVariable().toString(), vb.getVariable().getSyntaxString());
        } catch (Exception e) {
            log.error("SNMP SET失败 {}:{} oid={} - 发生了底层异常:", host, port, oid, e);
            return SnmpResult.error(oid, e.getMessage());
        }
    }

    /**
     * SNMP SET请求（批量设置多个OID）
     */
    public List<SnmpResult> set(String host, int port, Map<String, String> oidValues,
                                String defaultType, String protocol, DeviceInfo v3Device) {
        List<SnmpResult> results = new ArrayList<>();
        try {
            Target<?> target = createSetTarget(host, port, protocol, v3Device);
            PDU pdu = createSetPDU(protocol);

            for (Map.Entry<String, String> entry : oidValues.entrySet()) {
                VariableBinding vb = createVariableBinding(entry.getKey(), entry.getValue(), defaultType);
                pdu.add(vb);
            }

            ResponseEvent<?> event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) {
                for (String oid : oidValues.keySet()) {
                    results.add(SnmpResult.error(oid, "Timeout - no response"));
                }
                return results;
            }

            PDU response = event.getResponse();
            if (response.getErrorStatus() != PDU.noError) {
                String errMsg = response.getErrorStatusText();
                for (String oid : oidValues.keySet()) {
                    results.add(SnmpResult.error(oid, errMsg));
                }
                return results;
            }

            for (VariableBinding vb : response.getVariableBindings()) {
                results.add(SnmpResult.success(vb.getOid().toString(), vb.getVariable().toString(),
                        vb.getVariable().getSyntaxString()));
            }
        } catch (Exception e) {
            log.error("SNMP批量SET失败 {}:{} - 发生了底层异常:", host, port, e);
            for (String oid : oidValues.keySet()) {
                results.add(SnmpResult.error(oid, e.getMessage()));
            }
        }
        return results;
    }

    private Target<?> createSetTarget(String host, int port, String protocol, DeviceInfo v3Device) {
        return createSetTarget(host, port, protocol, v3Device, null);
    }

    private Target<?> createSetTarget(String host, int port, String protocol, DeviceInfo v3Device, String communityOverride) {
        Address address = GenericAddress.parse("udp:" + host + "/" + port);
        if ("SNMPv3".equalsIgnoreCase(protocol)) {
            return createV3Target(address, v3Device);
        } else {
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setAddress(address);
            String community = (communityOverride != null && !communityOverride.trim().isEmpty())
                    ? communityOverride.trim()
                    : snmpConfig.getV2c().getCommunityWrite();
            target.setCommunity(new OctetString(community));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(snmpConfig.getTimeout());
            target.setRetries(snmpConfig.getRetries());
            return target;
        }
    }

    private PDU createSetPDU(String oid, String value, String type, String protocol) {
        PDU pdu = createSetPDU(protocol);
        pdu.add(createVariableBinding(oid, value, type));
        return pdu;
    }

    private PDU createSetPDU(String protocol) {
        if ("SNMPv3".equalsIgnoreCase(protocol)) {
            ScopedPDU pdu = new ScopedPDU();
            pdu.setType(PDU.SET);
            return pdu;
        } else {
            PDU pdu = new PDU();
            pdu.setType(PDU.SET);
            return pdu;
        }
    }

    private VariableBinding createVariableBinding(String oid, String value, String type) {
        Variable variable;
        String typeUpper = (type != null) ? type.toUpperCase() : "STRING";

        switch (typeUpper) {
            case "INTEGER":
            case "INT":
                variable = new Integer32(Integer.parseInt(value));
                break;
            case "COUNTER":
            case "COUNTER32":
                variable = new Counter32(Long.parseLong(value));
                break;
            case "COUNTER64":
                variable = new Counter64(Long.parseLong(value));
                break;
            case "GAUGE":
            case "GAUGE32":
            case "UNSIGNED32":
                variable = new Gauge32(Long.parseLong(value));
                break;
            case "TIMETICKS":
                variable = new TimeTicks(Long.parseLong(value));
                break;
            case "OID":
                variable = new OID(value);
                break;
            case "IPADDRESS":
            case "IP":
                variable = new IpAddress(value);
                break;
            case "STRING":
            case "OCTETSTRING":
            default:
                variable = new OctetString(value);
                break;
        }

        return new VariableBinding(new OID(oid), variable);
    }
}