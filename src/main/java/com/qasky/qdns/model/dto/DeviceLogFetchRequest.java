package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 设备日志拉取请求
 */
@Data
public class DeviceLogFetchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private List<String> deviceIds;
    private Integer count;
    private String since;
}
