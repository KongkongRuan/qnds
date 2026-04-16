package com.qasky.qdns.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一运维接口响应数据
 */
@Data
public class UnifiedCommandResponseData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String taskId;
    private String deviceId;
    private Integer code;
    private String operation;
    private String mode;
    private String status;
    private String statusMessage;
    private Object downstream;

    public static UnifiedCommandResponseData fromTaskRecord(UnifiedTaskRecord taskRecord) {
        UnifiedCommandResponseData data = new UnifiedCommandResponseData();
        data.setRequestId(taskRecord.getRequestId());
        data.setTaskId(taskRecord.getTaskId());
        data.setDeviceId(taskRecord.getDeviceId());
        data.setCode(taskRecord.getCode());
        data.setOperation(taskRecord.getOperation());
        data.setMode(taskRecord.getMode());
        data.setStatus(taskRecord.getStatus());
        data.setStatusMessage(taskRecord.getStatusMessage());
        data.setDownstream(taskRecord.getDownstream());
        return data;
    }
}
