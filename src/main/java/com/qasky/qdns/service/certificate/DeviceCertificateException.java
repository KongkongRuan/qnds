package com.qasky.qdns.service.certificate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证书编排异常，支持携带HTTP状态码和结构化响应数据。
 */
public class DeviceCertificateException extends RuntimeException {

    private final int httpStatus;
    private final Map<String, Object> data;

    public DeviceCertificateException(int httpStatus, String message) {
        this(httpStatus, message, null);
    }

    public DeviceCertificateException(int httpStatus, String message, Map<String, Object> data) {
        super(message);
        this.httpStatus = httpStatus;
        this.data = data != null
                ? Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data))
                : Collections.<String, Object>emptyMap();
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
