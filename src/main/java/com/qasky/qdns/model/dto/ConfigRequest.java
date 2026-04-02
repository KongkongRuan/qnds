package com.qasky.qdns.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 配置下发请求
 */
@Data
public class ConfigRequest {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 配置项列表
     */
    private List<ConfigItem> configs;

    @Data
    public static class ConfigItem {
        /**
         * 配置OID
         */
        private String oid;

        /**
         * 配置值
         */
        private String value;

        /**
         * 值类型（INTEGER/STRING/OID/IPADDRESS等）
         */
        private String type = "STRING";

        /**
         * 配置描述（可选）
         */
        private String description;
    }
}
