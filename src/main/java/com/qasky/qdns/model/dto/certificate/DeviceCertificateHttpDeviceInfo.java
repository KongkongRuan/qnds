package com.qasky.qdns.model.dto.certificate;

import lombok.Data;

import java.io.Serializable;

/**
 * 自建CA签发接口返回的设备基础信息。
 */
@Data
public class DeviceCertificateHttpDeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String deviceName;
    private String deviceIp;
    private String devicePort;
    private String manufacturer;
    private String deviceType;
    private String deviceModel;
}
