package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量设备日志拉取结果
 */
@Data
public class DeviceLogBatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer totalDevices;
    private Integer successCount;
    private Integer failCount;
    private List<DeviceLogFetchResult> results;
    private Long totalTime;
}
