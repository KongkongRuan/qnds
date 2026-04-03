package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单设备日志拉取结果
 */
@Data
public class DeviceLogFetchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String deviceIp;
    private String devicePort;
    private Boolean success;
    private String fetchMethod;
    private List<DeviceLogEntry> logs;
    private Integer logCount;
    private String errorMessage;
    private Long fetchTime;
}
