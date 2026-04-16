package com.qasky.qdns.service.certificate;

import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import com.qasky.qdns.model.dto.UnifiedCommandResponseData;
import com.qasky.qdns.model.dto.certificate.DeviceCsrRequest;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueRequest;
import com.qasky.qdns.model.dto.certificate.ThirdPartyInstallRequest;
import com.qasky.qdns.service.unified.UnifiedCommandService;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 设备证书编排服务。
 */
@Service
public class DeviceCertificateService {

    private static final int CODE_UPLOAD_CA_CHAIN = 10009;
    private static final int CODE_UPLOAD_CERT = 10010;
    private static final int CODE_QUERY_CERT_STATUS = 10011;
    private static final int CODE_READ_DEVICE_CERT = 10012;
    private static final int CODE_READ_CA_CHAIN = 10013;
    private static final int CODE_READ_DEVICE_CSR = 10014;

    private static final String OP_UPLOAD_CA_CHAIN = "upload_ca_chain";
    private static final String OP_UPLOAD_CERT = "upload_cert";
    private static final String OP_QUERY_CERT_STATUS = "query_cert_status";
    private static final String OP_READ_DEVICE_CERT = "read_device_cert";
    private static final String OP_READ_CA_CHAIN = "read_ca_chain";
    private static final String OP_READ_DEVICE_CSR = "read_device_csr";

    private final UnifiedCommandService unifiedCommandService;
    private final CAServiceClient caServiceClient;

    public DeviceCertificateService(UnifiedCommandService unifiedCommandService,
                                    CAServiceClient caServiceClient) {
        this.unifiedCommandService = unifiedCommandService;
        this.caServiceClient = caServiceClient;
    }

    public Map<String, Object> queryCertificateStatus(String deviceId) {
        UnifiedCommandResponseData response = executeRequiredCommand(
                deviceId, CODE_QUERY_CERT_STATUS, OP_QUERY_CERT_STATUS, Collections.<String, Object>emptyMap(),
                502, "查询设备证书状态"
        );
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("status", extractDownstreamMap(response));
        return result;
    }

    public Map<String, Object> syncDeviceCertificate(String deviceId) {
        UnifiedCommandResponseData statusResponse = executeRequiredCommand(
                deviceId, CODE_QUERY_CERT_STATUS, OP_QUERY_CERT_STATUS, Collections.<String, Object>emptyMap(),
                502, "查询设备证书状态"
        );
        UnifiedCommandResponseData certResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CERT, OP_READ_DEVICE_CERT, Collections.<String, Object>emptyMap(),
                502, "读取设备证书"
        );
        UnifiedCommandResponseData caChainResponse = executeRequiredCommand(
                deviceId, CODE_READ_CA_CHAIN, OP_READ_CA_CHAIN, Collections.<String, Object>emptyMap(),
                502, "读取CA证书链"
        );
        UnifiedCommandResponseData csrResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CSR, OP_READ_DEVICE_CSR, Collections.<String, Object>emptyMap(),
                502, "读取设备CSR"
        );

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("status", commandResult(statusResponse));
        result.put("deviceCertificate", commandResult(certResponse));
        result.put("caChain", commandResult(caChainResponse));
        result.put("csr", commandResult(csrResponse));
        return result;
    }

    public Map<String, Object> readDeviceCertificate(String deviceId) {
        UnifiedCommandResponseData certResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CERT, OP_READ_DEVICE_CERT, Collections.<String, Object>emptyMap(),
                502, "读取设备证书"
        );
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("deviceCertificate", commandResult(certResponse));
        return result;
    }

    public Map<String, Object> readCaChain(String deviceId) {
        UnifiedCommandResponseData caChainResponse = executeRequiredCommand(
                deviceId, CODE_READ_CA_CHAIN, OP_READ_CA_CHAIN, Collections.<String, Object>emptyMap(),
                502, "读取CA证书链"
        );
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("caChain", commandResult(caChainResponse));
        return result;
    }

    public Map<String, Object> getOrCreateCsr(String deviceId, DeviceCsrRequest request) {
        UnifiedCommandResponseData csrResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CSR, OP_READ_DEVICE_CSR, buildCsrPayload(request),
                502, "读取设备CSR"
        );
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("csr", commandResult(csrResponse));
        return result;
    }

    public Map<String, Object> issueSelfCa(String deviceId, SelfCaIssueRequest request) {
        DeviceCsrRequest csrRequest = new DeviceCsrRequest();
        if (request != null) {
            csrRequest.setAlgorithm(request.getAlgorithm());
            csrRequest.setForceRegenerate(request.getForceRegenerate());
            csrRequest.setCommonName(request.getCommonName());
        }
        UnifiedCommandResponseData csrResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CSR, OP_READ_DEVICE_CSR, buildCsrPayload(csrRequest),
                502, "读取设备CSR"
        );

        Map<String, Object> csrData = extractDownstreamMap(csrResponse);
        String algorithm = normalizeAlgorithm(asString(csrData.get("algorithm")));
        String csrPemBase64 = requireText(csrData.get("csrPemBase64"), "设备CSR不能为空");

        Map<String, Object> issueResult = caServiceClient.signCertificate(
                csrPemBase64, algorithm, request != null ? request.getValidDays() : null
        );
        String deviceCertPemBase64 = requireText(issueResult.get("deviceCertPemBase64"), "自建CA未返回设备证书");
        Map<String, Object> caChainResult = caServiceClient.getCaCertificate(algorithm);

        Map<String, Object> uploadCaChainPayload = new LinkedHashMap<String, Object>();
        uploadCaChainPayload.put("source", "self_ca");
        uploadCaChainPayload.put("caChainPemBase64List", caChainResult.get("caChainPemBase64List"));
        UnifiedCommandResponseData uploadCaChainResponse = executeRequiredCommand(
                deviceId, CODE_UPLOAD_CA_CHAIN, OP_UPLOAD_CA_CHAIN, uploadCaChainPayload,
                502, "下发CA证书链"
        );

        Map<String, Object> uploadCertPayload = new LinkedHashMap<String, Object>();
        uploadCertPayload.put("source", "self_ca");
        uploadCertPayload.put("deviceCertPemBase64", deviceCertPemBase64);
        UnifiedCommandResponseData uploadCertResponse = executeCommand(
                deviceId, CODE_UPLOAD_CERT, OP_UPLOAD_CERT, uploadCertPayload
        );
        if (!isSuccess(uploadCertResponse)) {
            throw partialInstallFailure("设备证书安装失败，CA证书链已安装", deviceId, uploadCaChainResponse, uploadCertResponse);
        }

        UnifiedCommandResponseData statusResponse = executeRequiredCommand(
                deviceId, CODE_QUERY_CERT_STATUS, OP_QUERY_CERT_STATUS, Collections.<String, Object>emptyMap(),
                502, "回读设备证书状态"
        );
        UnifiedCommandResponseData readDeviceCertResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CERT, OP_READ_DEVICE_CERT, Collections.<String, Object>emptyMap(),
                502, "回读设备证书"
        );

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("flow", "self_ca");
        result.put("csr", commandResult(csrResponse));
        result.put("caIssue", issueResult);
        result.put("caChain", caChainResult);
        result.put("install", installResult(uploadCaChainResponse, uploadCertResponse));

        Map<String, Object> verification = new LinkedHashMap<String, Object>();
        verification.put("queryCertStatus", commandResult(statusResponse));
        verification.put("readDeviceCert", commandResult(readDeviceCertResponse));
        verification.put("caStatusCheck", safeCaStatusCheck(deviceCertPemBase64));
        result.put("verification", verification);
        return result;
    }

    public Map<String, Object> installThirdParty(String deviceId, ThirdPartyInstallRequest request) {
        if (request == null) {
            throw new DeviceCertificateException(400, "请求体不能为空");
        }
        String deviceCertPemBase64 = normalizePemInput(request.getDeviceCertPemBase64(), "deviceCertPemBase64");
        List<String> caChainPemBase64List = normalizePemList(request.getCaChainPemBase64List(), "caChainPemBase64List");

        DeviceCsrRequest csrRequest = new DeviceCsrRequest();
        csrRequest.setAlgorithm(request.getAlgorithm());
        csrRequest.setForceRegenerate(request.getForceRegenerate());
        csrRequest.setCommonName(request.getCommonName());
        UnifiedCommandResponseData csrResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CSR, OP_READ_DEVICE_CSR, buildCsrPayload(csrRequest),
                502, "读取设备CSR"
        );

        Map<String, Object> uploadCaChainPayload = new LinkedHashMap<String, Object>();
        uploadCaChainPayload.put("source", "third_party");
        uploadCaChainPayload.put("caChainPemBase64List", caChainPemBase64List);
        UnifiedCommandResponseData uploadCaChainResponse = executeRequiredCommand(
                deviceId, CODE_UPLOAD_CA_CHAIN, OP_UPLOAD_CA_CHAIN, uploadCaChainPayload,
                502, "下发CA证书链"
        );

        Map<String, Object> uploadCertPayload = new LinkedHashMap<String, Object>();
        uploadCertPayload.put("source", "third_party");
        uploadCertPayload.put("deviceCertPemBase64", deviceCertPemBase64);
        UnifiedCommandResponseData uploadCertResponse = executeCommand(
                deviceId, CODE_UPLOAD_CERT, OP_UPLOAD_CERT, uploadCertPayload
        );
        if (!isSuccess(uploadCertResponse)) {
            throw partialInstallFailure("第三方设备证书安装失败，CA证书链已安装", deviceId, uploadCaChainResponse, uploadCertResponse);
        }

        UnifiedCommandResponseData statusResponse = executeRequiredCommand(
                deviceId, CODE_QUERY_CERT_STATUS, OP_QUERY_CERT_STATUS, Collections.<String, Object>emptyMap(),
                502, "回读设备证书状态"
        );
        UnifiedCommandResponseData readDeviceCertResponse = executeRequiredCommand(
                deviceId, CODE_READ_DEVICE_CERT, OP_READ_DEVICE_CERT, Collections.<String, Object>emptyMap(),
                502, "回读设备证书"
        );
        UnifiedCommandResponseData readCaChainResponse = executeRequiredCommand(
                deviceId, CODE_READ_CA_CHAIN, OP_READ_CA_CHAIN, Collections.<String, Object>emptyMap(),
                502, "回读CA证书链"
        );

        Map<String, Object> uploadedCertSummary = summarizeCertificate(deviceCertPemBase64);
        Map<String, Object> readDeviceCertData = extractDownstreamMap(readDeviceCertResponse);
        boolean fingerprintMatched = Objects.equals(
                uploadedCertSummary.get("fingerprint"),
                readDeviceCertData.get("deviceCertFingerprint")
        );

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deviceId", deviceId);
        result.put("flow", "third_party");
        result.put("csr", commandResult(csrResponse));
        result.put("submittedDeviceCertificate", uploadedCertSummary);
        result.put("submittedCaChainCount", caChainPemBase64List.size());
        result.put("install", installResult(uploadCaChainResponse, uploadCertResponse));

        Map<String, Object> verification = new LinkedHashMap<String, Object>();
        verification.put("queryCertStatus", commandResult(statusResponse));
        verification.put("readDeviceCert", commandResult(readDeviceCertResponse));
        verification.put("readCaChain", commandResult(readCaChainResponse));
        verification.put("uploadedCertFingerprint", uploadedCertSummary.get("fingerprint"));
        verification.put("readbackFingerprintMatched", fingerprintMatched);
        result.put("verification", verification);
        return result;
    }

    private Map<String, Object> buildCsrPayload(DeviceCsrRequest request) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (request == null) {
            return payload;
        }
        if (hasText(request.getAlgorithm())) {
            payload.put("algorithm", request.getAlgorithm().trim());
        }
        if (request.getForceRegenerate() != null) {
            payload.put("forceRegenerate", request.getForceRegenerate());
        }
        if (hasText(request.getCommonName())) {
            payload.put("commonName", request.getCommonName().trim());
        }
        return payload;
    }

    private UnifiedCommandResponseData executeRequiredCommand(String deviceId,
                                                             int code,
                                                             String operation,
                                                             Map<String, Object> payload,
                                                             int httpStatus,
                                                             String actionLabel) {
        UnifiedCommandResponseData response = executeCommand(deviceId, code, operation, payload);
        if (!isSuccess(response)) {
            throw stepFailure(httpStatus, actionLabel + "失败: " + response.getStatusMessage(), response);
        }
        return response;
    }

    private UnifiedCommandResponseData executeCommand(String deviceId,
                                                      int code,
                                                      String operation,
                                                      Map<String, Object> payload) {
        UnifiedCommandRequest request = new UnifiedCommandRequest();
        request.setDeviceId(deviceId);
        request.setCode(code);
        request.setOperation(operation);
        request.setPayload(payload != null ? payload : new LinkedHashMap<String, Object>());
        request.setOperator("device-certificate-service");
        return unifiedCommandService.executeCommand(request);
    }

    private boolean isSuccess(UnifiedCommandResponseData response) {
        return response != null && "SUCCESS".equalsIgnoreCase(response.getStatus());
    }

    private DeviceCertificateException stepFailure(int httpStatus,
                                                   String message,
                                                   UnifiedCommandResponseData response) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("step", response != null ? response.getOperation() : "");
        data.put("response", commandResult(response));
        return new DeviceCertificateException(httpStatus, message, data);
    }

    private DeviceCertificateException partialInstallFailure(String message,
                                                            String deviceId,
                                                            UnifiedCommandResponseData uploadCaChainResponse,
                                                            UnifiedCommandResponseData uploadCertResponse) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("deviceId", deviceId);
        data.put("partial", true);
        data.put("install", installResult(uploadCaChainResponse, uploadCertResponse));
        return new DeviceCertificateException(502, message, data);
    }

    private Map<String, Object> installResult(UnifiedCommandResponseData uploadCaChainResponse,
                                              UnifiedCommandResponseData uploadCertResponse) {
        Map<String, Object> install = new LinkedHashMap<String, Object>();
        install.put("uploadCaChain", commandResult(uploadCaChainResponse));
        install.put("uploadCert", commandResult(uploadCertResponse));
        return install;
    }

    private Map<String, Object> safeCaStatusCheck(String certPemBase64) {
        try {
            Map<String, Object> data = caServiceClient.queryCertificateStatus(certPemBase64);
            data.put("success", true);
            return data;
        } catch (DeviceCertificateException e) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("success", false);
            result.put("message", e.getMessage());
            result.put("httpStatus", e.getHttpStatus());
            result.put("data", e.getData());
            return result;
        }
    }

    private Map<String, Object> commandResult(UnifiedCommandResponseData response) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (response == null) {
            return result;
        }
        result.put("requestId", response.getRequestId());
        result.put("code", response.getCode());
        result.put("operation", response.getOperation());
        result.put("mode", response.getMode());
        result.put("status", response.getStatus());
        result.put("statusMessage", response.getStatusMessage());
        result.put("downstream", response.getDownstream());
        return result;
    }

    private Map<String, Object> extractDownstreamMap(UnifiedCommandResponseData response) {
        Object downstream = response != null ? response.getDownstream() : null;
        if (downstream instanceof Map) {
            return (Map<String, Object>) downstream;
        }
        return Collections.emptyMap();
    }

    private String normalizeAlgorithm(String value) {
        String normalized = value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!"RSA".equals(normalized) && !"SM2".equals(normalized)) {
            throw new DeviceCertificateException(400, "不支持的CSR算法: " + value);
        }
        return normalized;
    }

    private String normalizePemInput(String value, String fieldName) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            throw new DeviceCertificateException(400, fieldName + "不能为空");
        }
        if (text.contains("-----BEGIN ")) {
            return encodePemText(text);
        }
        try {
            String pemText = new String(Base64.getMimeDecoder().decode(text), StandardCharsets.UTF_8);
            if (!pemText.contains("-----BEGIN ")) {
                throw new DeviceCertificateException(400, fieldName + "格式不正确");
            }
            return encodePemText(pemText);
        } catch (IllegalArgumentException e) {
            throw new DeviceCertificateException(400, fieldName + "格式不正确");
        }
    }

    private List<String> normalizePemList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new DeviceCertificateException(400, fieldName + "不能为空");
        }
        List<String> normalized = new ArrayList<String>();
        for (String value : values) {
            normalized.add(normalizePemInput(value, fieldName));
        }
        return normalized;
    }

    private Map<String, Object> summarizeCertificate(String certPemBase64) {
        try {
            String pemText = decodePemText(certPemBase64);
            byte[] derBytes = pemToDer(pemText);
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(derBytes));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fingerprint = digest.digest(certificate.getEncoded());

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("serialNumber", certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT));
            result.put("fingerprint", toHex(fingerprint));
            result.put("subject", principalName(certificate.getSubjectX500Principal()));
            result.put("issuer", principalName(certificate.getIssuerX500Principal()));
            result.put("notBefore", formatDate(certificate.getNotBefore()));
            result.put("notAfter", formatDate(certificate.getNotAfter()));
            result.put("certPemBase64", certPemBase64);
            return result;
        } catch (DeviceCertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new DeviceCertificateException(400, "解析设备证书失败: " + e.getMessage());
        }
    }

    private String encodePemText(String pemText) {
        String normalized = pemText.replace("\r\n", "\n").replace("\r", "\n").trim() + "\n";
        return Base64.getEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePemText(String certPemBase64) {
        try {
            return new String(Base64.getMimeDecoder().decode(certPemBase64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new DeviceCertificateException(400, "证书内容不是合法的Base64 PEM");
        }
    }

    private byte[] pemToDer(String pemText) {
        String sanitized = pemText
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        return Base64.getMimeDecoder().decode(sanitized);
    }

    private String principalName(X500Principal principal) {
        return principal != null ? principal.getName(X500Principal.RFC2253) : "";
    }

    private String formatDate(Date value) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.ROOT, "%02X", value[i]));
        }
        return builder.toString();
    }

    private String requireText(Object value, String message) {
        String text = asString(value);
        if (!hasText(text)) {
            throw new DeviceCertificateException(502, message);
        }
        return text.trim();
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
