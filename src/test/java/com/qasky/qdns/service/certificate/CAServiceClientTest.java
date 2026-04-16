package com.qasky.qdns.service.certificate;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CAServiceClientTest {

    @Test
    void shouldPropagateCaFailureFromSignCertificate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CAServiceClient client = new CAServiceClient(restTemplate, "http://127.0.0.1:18892/ca", 365);

        when(restTemplate.postForObject(eq("http://127.0.0.1:18892/ca/certs"), any(), eq(Map.class)))
                .thenReturn(mapOf(
                        "code", 500,
                        "message", "Signature verification failed！"
                ));

        DeviceCertificateException exception = assertThrows(
                DeviceCertificateException.class,
                () -> client.signCertificate("CSR-B64", "SM2", null)
        );

        assertEquals(502, exception.getHttpStatus());
        assertEquals("CAService /certs 调用失败: Signature verification failed！", exception.getMessage());
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
