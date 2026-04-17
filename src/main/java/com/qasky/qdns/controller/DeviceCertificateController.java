package com.qasky.qdns.controller;

import com.qasky.qdns.model.ApiResponse;
import com.qasky.qdns.model.dto.certificate.DeviceCsrRequest;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueRequest;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueHttpResponse;
import com.qasky.qdns.model.dto.certificate.ThirdPartyInstallRequest;
import com.qasky.qdns.service.certificate.DeviceCertificateException;
import com.qasky.qdns.service.certificate.DeviceCertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 设备证书编排接口。
 */
@RestController
@RequestMapping("/api/device-certificates")
public class DeviceCertificateController {

    private static final Logger log = LoggerFactory.getLogger(DeviceCertificateController.class);

    private final DeviceCertificateService deviceCertificateService;

    public DeviceCertificateController(DeviceCertificateService deviceCertificateService) {
        this.deviceCertificateService = deviceCertificateService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryStatusByParam(@RequestParam Map<String, String> params) {
        return queryStatusInternal(firstNonBlank(params.get("deviceId"), params.get("id")));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryStatus(@PathVariable String deviceId) {
        return queryStatusInternal(deviceId);
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncByBody(@RequestBody(required = false) Map<String, Object> body) {
        return syncInternal(deviceIdFromMap(body));
    }

    @PostMapping("/{deviceId}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sync(@PathVariable String deviceId) {
        return syncInternal(deviceId);
    }

    @GetMapping("/device-cert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readDeviceCertByParam(@RequestParam Map<String, String> params) {
        return readDeviceCertInternal(firstNonBlank(params.get("deviceId"), params.get("id")));
    }

    @GetMapping("/{deviceId}/device-cert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readDeviceCert(@PathVariable String deviceId) {
        return readDeviceCertInternal(deviceId);
    }

    @GetMapping("/ca-chain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readCaChainByParam(@RequestParam Map<String, String> params) {
        return readCaChainInternal(firstNonBlank(params.get("deviceId"), params.get("id")));
    }

    @GetMapping("/{deviceId}/ca-chain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readCaChain(@PathVariable String deviceId) {
        return readCaChainInternal(deviceId);
    }

    @GetMapping("/csr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCsrByParam(@RequestParam Map<String, String> params) {
        DeviceCsrRequest request = new DeviceCsrRequest();
        request.setAlgorithm(params.get("algorithm"));
        request.setForceRegenerate(parseBoolean(params.get("forceRegenerate")));
        request.setCommonName(params.get("commonName"));
        return getCsrInternal(firstNonBlank(params.get("deviceId"), params.get("id")), request);
    }

    @GetMapping("/{deviceId}/csr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCsr(@PathVariable String deviceId,
                                                                   @RequestParam(required = false) String algorithm,
                                                                   @RequestParam(required = false) Boolean forceRegenerate,
                                                                   @RequestParam(required = false) String commonName) {
        DeviceCsrRequest request = new DeviceCsrRequest();
        request.setAlgorithm(algorithm);
        request.setForceRegenerate(forceRegenerate);
        request.setCommonName(commonName);
        return getCsrInternal(deviceId, request);
    }

    @PostMapping("/issue/self-ca")
    public ResponseEntity<ApiResponse<SelfCaIssueHttpResponse>> issueSelfCaByBody(@RequestBody(required = false) SelfCaIssueRequest request) {
        return issueSelfCaInternal(request != null ? request.getDeviceId() : null, request);
    }

    @PostMapping("/{deviceId}/issue/self-ca")
    public ResponseEntity<ApiResponse<SelfCaIssueHttpResponse>> issueSelfCa(@PathVariable String deviceId,
                                                                            @RequestBody(required = false) SelfCaIssueRequest request) {
        return issueSelfCaInternal(deviceId, request);
    }

    @PostMapping("/install/third-party")
    public ResponseEntity<ApiResponse<Map<String, Object>>> installThirdPartyByBody(@RequestBody(required = false) ThirdPartyInstallRequest request) {
        return installThirdPartyInternal(request != null ? request.getDeviceId() : null, request);
    }

    @PostMapping("/{deviceId}/install/third-party")
    public ResponseEntity<ApiResponse<Map<String, Object>>> installThirdParty(@PathVariable String deviceId,
                                                                              @RequestBody(required = false) ThirdPartyInstallRequest request) {
        return installThirdPartyInternal(deviceId, request);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> queryStatusInternal(String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.queryCertificateStatus(requireDeviceId(deviceId))));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("查询设备证书状态异常", e);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> syncInternal(String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.syncDeviceCertificate(requireDeviceId(deviceId))));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("同步设备证书信息异常", e);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> readDeviceCertInternal(String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.readDeviceCertificate(requireDeviceId(deviceId))));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("读取设备证书异常", e);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> readCaChainInternal(String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.readCaChain(requireDeviceId(deviceId))));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("读取CA证书链异常", e);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> getCsrInternal(String deviceId,
                                                                            DeviceCsrRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.getOrCreateCsr(requireDeviceId(deviceId), request)));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("读取设备CSR异常", e);
        }
    }

    private ResponseEntity<ApiResponse<SelfCaIssueHttpResponse>> issueSelfCaInternal(String deviceId,
                                                                                     SelfCaIssueRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.issueSelfCa(requireDeviceId(deviceId), request)));
        } catch (DeviceCertificateException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiResponse<SelfCaIssueHttpResponse>(e.getHttpStatus(), e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<SelfCaIssueHttpResponse>(400, e.getMessage(), null));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<SelfCaIssueHttpResponse>(404, e.getMessage(), null));
        } catch (Exception e) {
            log.error("自建CA签发并下发异常", e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse<SelfCaIssueHttpResponse>(500, "自建CA签发并下发异常: " + e.getMessage(), null));
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> installThirdPartyInternal(String deviceId,
                                                                                       ThirdPartyInstallRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.installThirdParty(requireDeviceId(deviceId), request)));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("安装第三方证书异常", e);
        }
    }

    private String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceId不能为空");
        }
        return deviceId.trim();
    }

    private String deviceIdFromMap(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object deviceId = body.get("deviceId");
        if (deviceId == null) {
            deviceId = body.get("id");
        }
        return deviceId != null ? String.valueOf(deviceId) : null;
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Boolean.valueOf(value.trim());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> errorResponse(DeviceCertificateException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(new ApiResponse<Map<String, Object>>(e.getHttpStatus(), e.getMessage(), mutableCopy(e.getData())));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ApiResponse<Map<String, Object>>(400, e.getMessage(), new LinkedHashMap<String, Object>()));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> notFound(Exception e) {
        return ResponseEntity.status(404).body(new ApiResponse<Map<String, Object>>(404, e.getMessage(), new LinkedHashMap<String, Object>()));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> internalError(String message, Exception e) {
        log.error(message, e);
        return ResponseEntity.status(500)
                .body(new ApiResponse<Map<String, Object>>(500, message + ": " + e.getMessage(), new LinkedHashMap<String, Object>()));
    }

    private Map<String, Object> mutableCopy(Map<String, Object> data) {
        return data != null ? new LinkedHashMap<String, Object>(data) : new LinkedHashMap<String, Object>();
    }
}
