package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一运维接口请求体
 */
@Data
public class UnifiedCommandRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求ID，可选；未传时由节点生成
     */
    private String requestId;

    /**
     * 平台设备ID
     */
    private String deviceId;

    /**
     * 业务能力编码
     */
    private Integer code;

    /**
     * 具体操作
     */
    private String operation;

    /**
     * 业务参数
     */
    private Map<String, Object> payload = new LinkedHashMap<>();

    /**
     * 操作人/来源系统
     */
    private String operator;
}
