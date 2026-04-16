package com.qasky.qdns.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 统一运维异步任务状态
 */
@Data
public class UnifiedTaskRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private String requestId;
    private String deviceId;
    private Integer code;
    private String operation;
    private String mode;
    private String status;
    private String statusMessage;
    private Object downstream;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date finishedAt;
}
