package com.qasky.qdns.controller;

import com.qasky.qdns.model.ApiResponse;
import com.qasky.qdns.model.dto.certificate.DeviceCsrRequest;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueRequest;
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

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryStatus(@PathVariable String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.queryCertificateStatus(deviceId)));
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

    @PostMapping("/{deviceId}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sync(@PathVariable String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.syncDeviceCertificate(deviceId)));
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

    @GetMapping("/{deviceId}/device-cert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readDeviceCert(@PathVariable String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.readDeviceCertificate(deviceId)));
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

    @GetMapping("/{deviceId}/ca-chain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readCaChain(@PathVariable String deviceId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.readCaChain(deviceId)));
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

    @GetMapping("/{deviceId}/csr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCsr(@PathVariable String deviceId,
                                                                   @RequestParam(required = false) String algorithm,
                                                                   @RequestParam(required = false) Boolean forceRegenerate,
                                                                   @RequestParam(required = false) String commonName) {
        try {
            DeviceCsrRequest request = new DeviceCsrRequest();
            request.setAlgorithm(algorithm);
            request.setForceRegenerate(forceRegenerate);
            request.setCommonName(commonName);
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.getOrCreateCsr(deviceId, request)));
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

    @PostMapping("/{deviceId}/issue/self-ca")
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueSelfCa(@PathVariable String deviceId,
                                                                        @RequestBody(required = false) SelfCaIssueRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.issueSelfCa(deviceId, request)));
        } catch (DeviceCertificateException e) {
            return errorResponse(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            return internalError("自建CA签发并下发异常", e);
        }
    }

    @PostMapping("/{deviceId}/install/third-party")
    public ResponseEntity<ApiResponse<Map<String, Object>>> installThirdParty(@PathVariable String deviceId,
                                                                              @RequestBody(required = false) ThirdPartyInstallRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(deviceCertificateService.installThirdParty(deviceId, request)));
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
