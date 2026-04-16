package com.qasky.qdns.model.dto.certificate;

import lombok.Data;

import java.io.Serializable;

/**
 * 读取或生成设备CSR请求。
 */
@Data
public class DeviceCsrRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String algorithm;
    private Boolean forceRegenerate;
    private String commonName;
}
