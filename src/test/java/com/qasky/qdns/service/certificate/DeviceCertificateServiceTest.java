package com.qasky.qdns.service.certificate;

import com.qasky.qdns.model.DeviceInfo;
import com.qasky.qdns.model.dto.UnifiedCommandRequest;
import com.qasky.qdns.model.dto.UnifiedCommandResponseData;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueHttpResponse;
import com.qasky.qdns.model.dto.certificate.SelfCaIssueRequest;
import com.qasky.qdns.model.dto.certificate.ThirdPartyInstallRequest;
import com.qasky.qdns.service.DeviceCollectorService;
import com.qasky.qdns.service.unified.UnifiedCommandService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceCertificateServiceTest {

    private static final String SAMPLE_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDEzCCAfugAwIBAgIUSKU/6VMqJwk8AQOjPhHqBlFPdGEwDQYJKoZIhvcNAQEL\n" +
            "BQAwGTEXMBUGA1UEAwwOUUROUyBUZXN0IENlcnQwHhcNMjYwNDE1MDQwOTI4WhcN\n" +
            "MjcwNDE1MDQwOTI4WjAZMRcwFQYDVQQDDA5RRE5TIFRlc3QgQ2VydDCCASIwDQYJ\n" +
            "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAIT37z+NH33XsQS4P0Z3WgzM1Td5si3V\n" +
            "v32a2zNqPEXWsJJvhGy7EQ4JhyCeBuKg39oo4idCo2+IFEQYtGEYo2i+A4h+u+Gc\n" +
            "y/KCUQM8sBNicwKUafeJkiV/mXhqkHJuhigh9Ek/+OMcHVm4k7tqXbdXLpv8sREs\n" +
            "elBSprHGamptFNynvJ7PYQrn+3S4V+MZc7iGqJWToI3eGVqy0tjiO5jsVj4KAPYD\n" +
            "VckSI+gBj9Taudyq5DSShc0rre255WG2bQuQMJurgJ/Uc1+SAfP9LYkTGukME3Bl\n" +
            "jKuMIacx/X2bIY4jf5lJ1hsBxLMoW/D51KIZyk1nUIJOgEiBfo7HbocCAwEAAaNT\n" +
            "MFEwHQYDVR0OBBYEFNM12YLM95McAyrz70YZneE5olMUMB8GA1UdIwQYMBaAFNM1\n" +
            "2YLM95McAyrz70YZneE5olMUMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEL\n" +
            "BQADggEBAEXO4nYR92brG5osWiE1m+aIbJWfL4AxkfQIhj3ASpPQM9OvmikZ16r5\n" +
            "qnNflJpsn40YXnLpJCiOmPwsTNZJzAZI9c7MKiJ1j2AUPg9RuCIBU/lJUhmve4tl\n" +
            "BLFyNcwon/zdcr4FwGBkkyb/MpOl8+4rbUfRmYlFPuBuvW8w3f3l697aZt1ZxRNA\n" +
            "JEtL392hojWRIEW+p3YPB+Ymzi1cIK4lCIXpbUnj1fCAnHCnSdW4MfFM+rzWp4uY\n" +
            "Wl3+nXSDVyxsLMMMPVgK4KC3WrjdtLSz6t09nacRsOHelQ+Nrzhiv3up7Z7AipXd\n" +
            "s+Dmh9IHmfC5mY+/QROMh0LHbFhVbuc=\n" +
            "-----END CERTIFICATE-----\n";

    private static final String SAMPLE_CERT_BASE64 = Base64.getEncoder()
            .encodeToString(SAMPLE_CERT_PEM.getBytes(StandardCharsets.UTF_8));

    private static final String SAMPLE_SM2_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICCjCCAa+gAwIBAgIGAZ2U815TMAoGCCqBHM9VAYN1MDUxFjAUBgNVBAMMDVFh\n" +
            "c2t5IENBIFJvb3QxDjAMBgNVBAoMBVFhc2t5MQswCQYDVQQGEwJDTjAeFw0yNjA0\n" +
            "MTYwODI3NDdaFw0zNjA0MTMwODI3NDdaMA8xDTALBgNVBAMMBDAwMDIwWTATBgcq\n" +
            "hkjOPQIBBggqgRzPVQGCLQNCAAT5dDAqlepp/CxFL7iSyAQcDJUVRhbZpItxYImR\n" +
            "8+9azSGeXeVTC2kQ3hGyJKimOhiaDnSwpYhLrXP2Z6ooPfJFo4HQMIHNMGIGA1Ud\n" +
            "IwRbMFmAFC4FBfuL8SCfQCGSQ6e38fd0a0yAoTmkNzA1MRYwFAYDVQQDDA1RYXNr\n" +
            "eSBDQSBSb290MQ4wDAYDVQQKDAVRYXNreTELMAkGA1UEBhMCQ06CBgGdekBRMjAd\n" +
            "BgNVHQ4EFgQUlEOesjehASBisvne5T25Xf8Qw+4wCQYDVR0TBAIwADAOBgNVHQ8B\n" +
            "Af8EBAMCA/gwLQYDVR0fBCYwJDAioCCgHoYcaHR0cDovL2NybC5xYXNreS5jb20v\n" +
            "c20yLnBlbTAKBggqgRzPVQGDdQNJADBGAiEAsx9WplkSbu/OKPufM7bsY4O6ly6l\n" +
            "RYLwqZbUVkOpC+gCIQClnQecf+4ZsDUHbxAQpvbPmuM6/ImH0989sZX3+66O6A==\n" +
            "-----END CERTIFICATE-----\n";

    private static final String SAMPLE_SM2_CERT_BASE64 = Base64.getEncoder()
            .encodeToString(SAMPLE_SM2_CERT_PEM.getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldOrchestrateSelfCaFlow() throws Exception {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        when(deviceCollectorService.getDeviceById("dev-1")).thenReturn(sampleDevice("dev-1"));
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10014, "read_device_csr", mapOf(
                        "algorithm", "RSA",
                        "csrPemBase64", "CSR-B64",
                        "csrSubject", "CN=device-1"
                )))
                .thenReturn(successResponse(10009, "upload_ca_chain", mapOf("caChainInstalled", true)))
                .thenReturn(successResponse(10010, "upload_cert", mapOf("deviceCertInstalled", true)))
                .thenReturn(successResponse(10011, "query_cert_status", mapOf(
                        "deviceCertInstalled", true,
                        "caChainInstalled", true
                )))
                .thenReturn(successResponse(10012, "read_device_cert", mapOf(
                        "deviceCertFingerprint", "AA:BB",
                        "deviceCertInstalled", true
                )));

        when(caServiceClient.signCertificate("CSR-B64", "RSA", 365))
                .thenReturn(mapOf(
                        "caType", "RSA",
                        "validDays", 365,
                        "deviceCertPemBase64", SAMPLE_CERT_BASE64
                ));
        when(caServiceClient.getCaCertificate("RSA"))
                .thenReturn(mapOf(
                        "caType", "RSA",
                        "caChainPemBase64List", Collections.singletonList(SAMPLE_CERT_BASE64)
                ));
        when(caServiceClient.queryCertificateStatus(SAMPLE_CERT_BASE64))
                .thenReturn(mapOf("result", 0));

        SelfCaIssueRequest request = new SelfCaIssueRequest();
        request.setAlgorithm("RSA");
        request.setValidDays(365);

        SelfCaIssueHttpResponse result = service.issueSelfCa("dev-1", request);

        assertNotNull(result.getDevice());
        assertEquals("dev-1", result.getDevice().getDeviceId());
        assertEquals("Device-dev-1", result.getDevice().getDeviceName());
        assertEquals("192.168.1.151", result.getDevice().getDeviceIp());
        assertNotNull(result.getDeviceCertificate());
        assertEquals(SAMPLE_CERT_PEM, result.getDeviceCertificate().getCertPem());
        assertEquals(SAMPLE_CERT_BASE64, result.getDeviceCertificate().getCertPemBase64());
        assertEquals("AA:BB", result.getDeviceCertificate().getFingerprint());
        assertEquals(serialDecimalOf(SAMPLE_CERT_PEM), result.getCaSerialNumber());

        verify(caServiceClient).signCertificate("CSR-B64", "RSA", 365);
        verify(caServiceClient).getCaCertificate("RSA");
        verify(caServiceClient).queryCertificateStatus(SAMPLE_CERT_BASE64);
    }

    @Test
    void shouldReturnPartialFailureWhenDeviceCertUploadFails() {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10014, "read_device_csr", mapOf(
                        "algorithm", "RSA",
                        "csrPemBase64", "CSR-B64"
                )))
                .thenReturn(successResponse(10009, "upload_ca_chain", mapOf("caChainInstalled", true)))
                .thenReturn(failedResponse(10010, "upload_cert", "certificate public key does not match device private key"));

        when(caServiceClient.signCertificate("CSR-B64", "RSA", null))
                .thenReturn(mapOf("caType", "RSA", "deviceCertPemBase64", SAMPLE_CERT_BASE64));
        when(caServiceClient.getCaCertificate("RSA"))
                .thenReturn(mapOf(
                        "caType", "RSA",
                        "caChainPemBase64List", Collections.singletonList(SAMPLE_CERT_BASE64)
                ));

        DeviceCertificateException exception = assertThrows(
                DeviceCertificateException.class,
                () -> service.issueSelfCa("dev-1", new SelfCaIssueRequest())
        );

        assertEquals(502, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("CA证书链已安装"));
        assertEquals(Boolean.TRUE, exception.getData().get("partial"));
        assertTrue(exception.getData().containsKey("install"));
    }

    @Test
    void shouldFailEarlyWhenSelfCaDoesNotReturnDeviceCertificate() {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10014, "read_device_csr", mapOf(
                        "algorithm", "SM2",
                        "csrPemBase64", "CSR-B64"
                )));

        when(caServiceClient.signCertificate("CSR-B64", "SM2", null))
                .thenReturn(mapOf("caType", "SM2"));

        DeviceCertificateException exception = assertThrows(
                DeviceCertificateException.class,
                () -> service.issueSelfCa("dev-1", new SelfCaIssueRequest())
        );

        assertEquals(502, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("自建CA未返回设备证书"));

        verify(unifiedCommandService, times(1)).executeCommand(any(UnifiedCommandRequest.class));
        verify(caServiceClient).signCertificate("CSR-B64", "SM2", null);
        verify(caServiceClient, never()).getCaCertificate(anyString());
    }

    @Test
    void shouldInstallThirdPartyCertificateAndMatchFingerprint() throws Exception {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        String expectedFingerprint = fingerprintOf(SAMPLE_CERT_PEM);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10014, "read_device_csr", mapOf(
                        "algorithm", "RSA",
                        "csrPemBase64", "CSR-B64"
                )))
                .thenReturn(successResponse(10009, "upload_ca_chain", mapOf("caChainInstalled", true)))
                .thenReturn(successResponse(10010, "upload_cert", mapOf("deviceCertInstalled", true)))
                .thenReturn(successResponse(10011, "query_cert_status", mapOf(
                        "deviceCertInstalled", true,
                        "caChainInstalled", true
                )))
                .thenReturn(successResponse(10012, "read_device_cert", mapOf(
                        "deviceCertFingerprint", expectedFingerprint,
                        "deviceCertInstalled", true
                )))
                .thenReturn(successResponse(10013, "read_ca_chain", mapOf(
                        "caChainInstalled", true,
                        "caChainCount", 1
                )));

        ThirdPartyInstallRequest request = new ThirdPartyInstallRequest();
        request.setDeviceCertPemBase64(SAMPLE_CERT_PEM);
        request.setCaChainPemBase64List(Collections.singletonList(SAMPLE_CERT_PEM));

        Map<String, Object> result = service.installThirdParty("dev-1", request);
        Map<String, Object> verification = asMap(result.get("verification"));
        assertEquals(Boolean.TRUE, verification.get("readbackFingerprintMatched"));

        ArgumentCaptor<UnifiedCommandRequest> captor = ArgumentCaptor.forClass(UnifiedCommandRequest.class);
        verify(unifiedCommandService, times(6)).executeCommand(captor.capture());
        List<UnifiedCommandRequest> requests = captor.getAllValues();
        assertEquals("read_device_csr", requests.get(0).getOperation());
        assertEquals("upload_ca_chain", requests.get(1).getOperation());
        assertEquals(Collections.singletonList(SAMPLE_CERT_BASE64), requests.get(1).getPayload().get("caChainPemBase64List"));
        assertEquals("upload_cert", requests.get(2).getOperation());
        assertEquals(SAMPLE_CERT_BASE64, requests.get(2).getPayload().get("deviceCertPemBase64"));
    }

    @Test
    void shouldReadDeviceCertificateOnly() {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10012, "read_device_cert", mapOf(
                        "deviceCertInstalled", true,
                        "deviceCertFingerprint", "AA:BB"
                )));

        Map<String, Object> result = service.readDeviceCertificate("dev-1");

        assertEquals("dev-1", result.get("deviceId"));
        Map<String, Object> deviceCertificate = asMap(result.get("deviceCertificate"));
        assertEquals("read_device_cert", deviceCertificate.get("operation"));

        ArgumentCaptor<UnifiedCommandRequest> captor = ArgumentCaptor.forClass(UnifiedCommandRequest.class);
        verify(unifiedCommandService).executeCommand(captor.capture());
        assertEquals("read_device_cert", captor.getValue().getOperation());
    }

    @Test
    void shouldReadCaChainOnly() {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10013, "read_ca_chain", mapOf(
                        "caChainInstalled", true,
                        "caChainCount", 1
                )));

        Map<String, Object> result = service.readCaChain("dev-1");

        assertEquals("dev-1", result.get("deviceId"));
        Map<String, Object> caChain = asMap(result.get("caChain"));
        assertEquals("read_ca_chain", caChain.get("operation"));

        ArgumentCaptor<UnifiedCommandRequest> captor = ArgumentCaptor.forClass(UnifiedCommandRequest.class);
        verify(unifiedCommandService).executeCommand(captor.capture());
        assertEquals("read_ca_chain", captor.getValue().getOperation());
    }

    @Test
    void shouldFallbackToGmJavaWhenSm2CertificateCannotBeParsedByJca() {
        UnifiedCommandService unifiedCommandService = mock(UnifiedCommandService.class);
        CAServiceClient caServiceClient = mock(CAServiceClient.class);
        DeviceCollectorService deviceCollectorService = mock(DeviceCollectorService.class);
        when(deviceCollectorService.getDeviceById("dev-sm2")).thenReturn(sampleDevice("dev-sm2"));
        DeviceCertificateService service = new DeviceCertificateService(unifiedCommandService, caServiceClient, deviceCollectorService);

        when(unifiedCommandService.executeCommand(any(UnifiedCommandRequest.class)))
                .thenReturn(successResponse(10014, "read_device_csr", mapOf(
                        "algorithm", "SM2",
                        "csrPemBase64", "CSR-B64",
                        "csrSubject", "CN=0002"
                )))
                .thenReturn(successResponse(10009, "upload_ca_chain", mapOf("caChainInstalled", true)))
                .thenReturn(successResponse(10010, "upload_cert", mapOf("deviceCertInstalled", true)))
                .thenReturn(successResponse(10011, "query_cert_status", mapOf(
                        "deviceCertInstalled", true,
                        "caChainInstalled", true
                )))
                .thenReturn(successResponse(10012, "read_device_cert", Collections.emptyMap()));

        when(caServiceClient.signCertificate("CSR-B64", "SM2", 365))
                .thenReturn(mapOf(
                        "caType", "SM2",
                        "validDays", 365,
                        "deviceCertPemBase64", SAMPLE_SM2_CERT_BASE64
                ));
        when(caServiceClient.getCaCertificate("SM2"))
                .thenReturn(mapOf(
                        "caType", "SM2",
                        "caChainPemBase64List", Collections.singletonList(SAMPLE_SM2_CERT_BASE64)
                ));
        when(caServiceClient.queryCertificateStatus(SAMPLE_SM2_CERT_BASE64))
                .thenReturn(mapOf("result", 0));

        SelfCaIssueRequest request = new SelfCaIssueRequest();
        request.setAlgorithm("SM2");
        request.setValidDays(365);

        SelfCaIssueHttpResponse result = service.issueSelfCa("dev-sm2", request);

        assertEquals("1776320470611", result.getCaSerialNumber());
        assertEquals(SAMPLE_SM2_CERT_PEM, result.getDeviceCertificate().getCertPem());
        assertEquals(SAMPLE_SM2_CERT_BASE64, result.getDeviceCertificate().getCertPemBase64());
    }

    private static UnifiedCommandResponseData successResponse(int code, String operation, Map<String, Object> downstream) {
        UnifiedCommandResponseData response = new UnifiedCommandResponseData();
        response.setRequestId(UUID.randomUUID().toString());
        response.setCode(code);
        response.setOperation(operation);
        response.setMode("SYNC");
        response.setStatus("SUCCESS");
        response.setStatusMessage("ok");
        response.setDownstream(downstream);
        return response;
    }

    private static UnifiedCommandResponseData failedResponse(int code, String operation, String message) {
        UnifiedCommandResponseData response = new UnifiedCommandResponseData();
        response.setRequestId(UUID.randomUUID().toString());
        response.setCode(code);
        response.setOperation(operation);
        response.setMode("SYNC");
        response.setStatus("FAILED");
        response.setStatusMessage(message);
        response.setDownstream(Collections.singletonMap("message", message));
        return response;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static String fingerprintOf(String pemText) throws Exception {
        String sanitized = pemText
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getMimeDecoder().decode(sanitized);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.ROOT, "%02X", digest[i]));
        }
        return builder.toString();
    }

    private static String serialDecimalOf(String pemText) throws Exception {
        String sanitized = pemText
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getMimeDecoder().decode(sanitized);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        return certificate.getSerialNumber().toString();
    }

    private static DeviceInfo sampleDevice(String deviceId) {
        DeviceInfo device = new DeviceInfo();
        device.setId(deviceId);
        device.setName("Device-" + deviceId);
        device.setDeviceIp("192.168.1.151");
        device.setDevicePort("161");
        device.setManufacturer("QASKY");
        device.setDeviceType("QVPN");
        device.setDeviceModel("QV-2000");
        return device;
    }
}
