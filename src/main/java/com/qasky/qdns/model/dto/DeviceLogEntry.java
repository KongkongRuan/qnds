package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备日志条目
 */
@Data
public class DeviceLogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String timestamp;
    private String level;
    private String module;
    private String message;
}
