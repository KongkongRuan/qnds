package com.qasky.qdns.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 告警信息模型
 */
@Data
public class AlarmInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 告警源（设备ID）
     */
    private String alarmSource;

    /**
     * 告警code
     */
    private String alarmCode;

    /**
     * 告警数据
     */
    private String alarmData;

    /**
     * 告警时间
     */
    private String alarmCreateDate;

    /**
     * 设备IP
     */
    private String deviceIp;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 告警级别（1-严重 2-重要 3-一般 4-提示）
     */
    private Integer alarmLevel;

    /**
     * Trap OID
     */
    private String trapOid;

    /**
     * 节点标识
     */
    private String nodeKey;
}
