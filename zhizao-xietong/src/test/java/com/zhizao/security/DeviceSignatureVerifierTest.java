package com.zhizao.security;

import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.DeviceCredential;
import com.zhizao.mapper.DeviceCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备签名校验测试：REST 与 MQTT 签名、时间窗口、停用凭证与规范串。
 *
 * @author Caspian
 */
class DeviceSignatureVerifierTest {

    private static final String SECRET = "device-secret-for-test";

    private DeviceCredentialMapper credentialMapper;
    private DeviceSignatureVerifier verifier;
    private DeviceCredential credential;

    /**
     * 构造开启鉴权的校验器与一条启用状态的凭证。
     */
    @BeforeEach
    void setUp() {
        DeviceSecretCipher cipher = new DeviceSecretCipher(
                Base64.getEncoder().encodeToString(new byte[32]));
        credentialMapper = mock(DeviceCredentialMapper.class);
        verifier = new DeviceSignatureVerifier(credentialMapper, cipher);
        ReflectionTestUtils.setField(verifier, "enabled", true);
        ReflectionTestUtils.setField(verifier, "toleranceSeconds", 300L);

        credential = new DeviceCredential();
        credential.setId(1L);
        credential.setDeviceId(1L);
        credential.setKeyId("DK-test");
        credential.setSecretCiphertext(cipher.encrypt(SECRET));
        credential.setEnabled(1);
    }

    @Test
    void rawPayloadWithValidSignaturePasses() {
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        String body = "{\"deviceNo\":\"EQ-1\"}";
        String signature = DeviceSignatureVerifier.hmacHex(SECRET, body);

        assertDoesNotThrow(() -> verifier.verifyRawPayload("DK-test", signature, body));
        verify(credentialMapper).updateById(credential);
        assertNotNull(credential.getLastUsedAt());
    }

    @Test
    void rawPayloadWithWrongSignatureRejected() {
        when(credentialMapper.selectOne(any())).thenReturn(credential);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", "deadbeef", "{}"));
        assertEquals(401, e.getStatus());
    }

    @Test
    void disabledCredentialRejectedWith403() {
        credential.setEnabled(0);
        when(credentialMapper.selectOne(any())).thenReturn(credential);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", "any", "{}"));
        assertEquals(403, e.getStatus());
    }

    @Test
    void mqttReportWithValidSignaturePasses() {
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        DeviceGatewayReport report = new DeviceGatewayReport("EQ-1", "RUNNING", 45.2, 1500.0, 8.5,
                "DK-test", Instant.now().toEpochMilli(), "n-1", null, "m-1");
        DeviceGatewayReport signed = new DeviceGatewayReport(report.deviceNo(), report.status(),
                report.temperature(), report.rpm(), report.current(), report.keyId(), report.timestamp(),
                report.nonce(), DeviceSignatureVerifier.hmacHex(SECRET,
                        DeviceSignatureVerifier.canonicalReport(report)), report.messageId());

        assertDoesNotThrow(() -> verifier.verifyReport(signed));
    }

    @Test
    void mqttReportOutsideToleranceRejected() {
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        long stale = Instant.now().minusSeconds(600).toEpochMilli();
        DeviceGatewayReport report = new DeviceGatewayReport("EQ-1", "RUNNING", 45.2, 1500.0, 8.5,
                "DK-test", stale, "n-1", "whatever", "m-1");

        DeviceAuthException e = assertThrows(DeviceAuthException.class, () -> verifier.verifyReport(report));
        assertEquals(401, e.getStatus());
    }

    @Test
    void enabledWithoutMasterKeyFailsAtStartup() {
        DeviceSignatureVerifier misconfigured = new DeviceSignatureVerifier(
                credentialMapper, new DeviceSecretCipher(""));
        ReflectionTestUtils.setField(misconfigured, "enabled", true);
        ReflectionTestUtils.setField(misconfigured, "toleranceSeconds", 300L);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                misconfigured::validateConfiguration);
        assertTrue(e.getMessage().contains("DEVICE_CREDENTIAL_KEY"));
    }

    @Test
    void disabledWithoutMasterKeyStartsNormally() {
        DeviceSignatureVerifier disabled = new DeviceSignatureVerifier(
                credentialMapper, new DeviceSecretCipher(""));

        assertDoesNotThrow(disabled::validateConfiguration);
    }

    @Test
    void canonicalReportUsesStableNumberFormat() {
        DeviceGatewayReport report = new DeviceGatewayReport("EQ-1", "RUNNING", 45.20, 1500.0, 8.50,
                "DK-test", 1700000000000L, "n-1", "sig", "m-1");

        assertEquals("EQ-1|RUNNING|45.2|1500|8.5|1700000000000|n-1",
                DeviceSignatureVerifier.canonicalReport(report));
    }

    @Test
    void logValueReplacesControlCharacters() {
        assertEquals("DK-x?fake?line", DeviceSignatureVerifier.sanitizeForLog("DK-x\nfake\rline"));
    }

    @Test
    void logValueIsTruncated() {
        String sanitized = DeviceSignatureVerifier.sanitizeForLog("a".repeat(500));

        assertEquals(64, sanitized.length());
    }

    @Test
    void logValueHandlesNull() {
        assertEquals("", DeviceSignatureVerifier.sanitizeForLog(null));
    }
}
