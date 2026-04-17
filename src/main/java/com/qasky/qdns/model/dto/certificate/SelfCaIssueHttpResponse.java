package com.qasky.qdns.model.dto.certificate;

import lombok.Data;

import java.io.Serializable;

/**
 * 自建CA签发并下发接口的精简HTTP返回对象。
 */
@Data
public class SelfCaIssueHttpResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private DeviceCertificateHttpDeviceInfo device;
    private DeviceCertificateHttpView deviceCertificate;
    /**
     * CA平台中保存的证书序列号，采用十进制字符串表示。
     */
    private String caSerialNumber;
}
