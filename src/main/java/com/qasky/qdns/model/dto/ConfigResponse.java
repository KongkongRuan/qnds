package com.qasky.qdns.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 配置下发响应
 */
@Data
public class ConfigResponse {

    private Integer code;
    private String message;
    private String deviceId;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private List<ConfigResult> results;

    @Data
    public static class ConfigResult {
        private String oid;
        private Boolean success;
        private String message;
        private String newValue;
    }
}
