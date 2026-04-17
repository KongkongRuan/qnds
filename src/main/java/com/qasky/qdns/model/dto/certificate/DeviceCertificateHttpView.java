package com.qasky.qdns.model.dto.certificate;

import lombok.Data;

import java.io.Serializable;

/**
 * HTTP返回的设备证书视图。
 */
@Data
public class DeviceCertificateHttpView implements Serializable {

    private static final long serialVersionUID = 1L;

    private String certPem;
    private String certPemBase64;
    private String fingerprint;
    private String subject;
    private String issuer;
    private String notBefore;
    private String notAfter;
}
