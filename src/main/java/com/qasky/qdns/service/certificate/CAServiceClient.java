package com.qasky.qdns.service.certificate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 自建CA服务客户端。
 */
@Service
public class CAServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final int defaultValidDays;

    @Autowired
    public CAServiceClient(@Value("${certificate.ca-service.base-url:http://127.0.0.1:18892/ca}") String baseUrl,
                           @Value("${certificate.ca-service.timeout.connect:5000}") int connectTimeout,
                           @Value("${certificate.ca-service.timeout.read:15000}") int readTimeout,
                           @Value("${certificate.ca-service.default-valid-days:365}") int defaultValidDays) {
        this(createRestTemplate(connectTimeout, readTimeout), baseUrl, defaultValidDays);
    }

    CAServiceClient(RestTemplate restTemplate, String baseUrl, int defaultValidDays) {
        this.restTemplate = restTemplate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.defaultValidDays = defaultValidDays;
    }

    public Map<String, Object> signCertificate(String csrPemBase64, String caType, Integer validDays) {
        String normalizedCaType = normalizeCaType(caType);
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("type", 1);
        request.put("csr", csrPemBase64);
        request.put("asymmetricAlgorithm", "SM2".equals(normalizedCaType) ? 1 : 0);
        if (validDays != null && validDays.intValue() > 0) {
            request.put("validDays", validDays);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> response = post("/certs", request);
        Map<String, Object> data = asMap(response.get("data"), "CAService /certs data");
        String certPemBase64 = asString(data.get("cert"));
        if (certPemBase64 == null || certPemBase64.trim().isEmpty()) {
            throw new DeviceCertificateException(502, "CAService 未返回设备证书");
        }

        result.put("caType", normalizedCaType);
        result.put("validDays", validDays != null && validDays.intValue() > 0 ? validDays : defaultValidDays);
        result.put("deviceCertPemBase64", certPemBase64);
        result.put("raw", response);

        return result;
    }

    public Map<String, Object> getCaCertificate(String caType) {
        String normalizedCaType = normalizeCaType(caType);
        Map<String, Object> response = get("/cacert/" + normalizedCaType);
        Map<String, Object> data = asMap(response.get("data"), "CAService /cacert data");
        String certPemBase64 = asString(data.get("cert"));
        if (certPemBase64 == null || certPemBase64.trim().isEmpty()) {
            throw new DeviceCertificateException(502, "CAService 未返回CA证书");
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("caType", normalizedCaType);
        result.put("caChainPemBase64List", Collections.singletonList(certPemBase64));
        result.put("raw", response);
        return result;
    }

    public Map<String, Object> queryCertificateStatus(String certPemBase64) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("cert", certPemBase64);
        Map<String, Object> response = post("/certs/status", request);
        Map<String, Object> data = asMap(response.get("data"), "CAService /certs/status data");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("result", data.get("result"));
        result.put("raw", response);
        return result;
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + path,
                    new HttpEntity<Map<String, Object>>(body, headers),
                    Map.class
            );
            return assertSuccess(response, path);
        } catch (RestClientException e) {
            throw new DeviceCertificateException(503, "CAService 调用失败: " + e.getMessage());
        }
    }

    private Map<String, Object> get(String path) {
        try {
            Map<String, Object> response = restTemplate.getForObject(baseUrl + path, Map.class);
            return assertSuccess(response, path);
        } catch (RestClientException e) {
            throw new DeviceCertificateException(503, "CAService 调用失败: " + e.getMessage());
        }
    }

    private Map<String, Object> assertSuccess(Map<String, Object> response, String path) {
        if (response == null) {
            throw new DeviceCertificateException(502, "CAService " + path + " 无响应");
        }
        Object code = response.get("code");
        if (!"0".equals(String.valueOf(code))) {
            String message = asString(response.get("message"));
            throw new DeviceCertificateException(502, "CAService " + path + " 调用失败: " + (message != null ? message : "unknown"));
        }
        return response;
    }

    private static RestTemplate createRestTemplate(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    private static Map<String, Object> asMap(Object value, String fieldName) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new DeviceCertificateException(502, fieldName + " 格式不正确");
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("certificate.ca-service.base-url不能为空");
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String normalizeCaType(String caType) {
        String normalized = caType != null ? caType.trim().toUpperCase(Locale.ROOT) : "";
        if (!"RSA".equals(normalized) && !"SM2".equals(normalized)) {
            throw new DeviceCertificateException(400, "不支持的CSR算法: " + caType);
        }
        return normalized;
    }
}
