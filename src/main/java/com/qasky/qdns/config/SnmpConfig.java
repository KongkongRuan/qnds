package com.qasky.qdns.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "snmp")
public class SnmpConfig {

    private String defaultProtocol = "SNMPv3";
    private V3Config v3 = new V3Config();
    private V2cConfig v2c = new V2cConfig();
    private SmConfig sm = new SmConfig();
    private int timeout = 3000;
    private int retries = 2;
    private int maxRepetitions = 20;

    @Data
    public static class SmConfig {
        private boolean legacyMode = true;
    }

    @Data
    public static class V3Config {
        private String username = "qasky";
        private String authPassword = "qasky1234";
        private String privPassword = "QaSky20191818";
        private String authProtocol = "SHA";
        private String privProtocol = "AES128";
    }

    @Data
    public static class V2cConfig {
        private String communityRead = "public";
        private String communityWrite = "private";
    }
}
