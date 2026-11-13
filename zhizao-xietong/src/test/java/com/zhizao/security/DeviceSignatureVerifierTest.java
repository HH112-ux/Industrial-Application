package com.zhizao.security;

import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.Device;
import com.zhizao.entity.DeviceCredential;
import com.zhizao.mapper.DeviceCredentialMapper;
import com.zhizao.mapper.DeviceMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备签名校验测试：REST 与 MQTT 签名、时间窗口、凭证与设备编号绑定、停用凭证与规范串。
 *
 * @author Caspian
 */
class DeviceSignatureVerifierTest {

    private static final String SECRET = "device-secret-for-test";
    private static final Long BOUND_DEVICE_ID = 1L;
    private static final String BOUND_DEVICE_NO = "EQ-1";

    private DeviceCredentialMapper credentialMapper;
    private DeviceMapper deviceMapper;
    private DeviceSignatureVerifier verifier;
    private DeviceCredential credential;
    private Device boundDevice;

    /**
     * 构造开启鉴权的校验器、一条启用状态的凭证与它绑定的设备。
     */
    @BeforeEach
    void setUp() {
        DeviceSecretCipher cipher = new DeviceSecretCipher(
                Base64.getEncoder().encodeToString(new byte[32]));
        credentialMapper = mock(DeviceCredentialMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        verifier = new DeviceSignatureVerifier(credentialMapper, deviceMapper, cipher);
        ReflectionTestUtils.setField(verifier, "enabled", true);
        ReflectionTestUtils.setField(verifier, "toleranceSeconds", 300L);

        credential = new DeviceCredential();
        credential.setId(1L);
        credential.setDeviceId(BOUND_DEVICE_ID);
        credential.setKeyId("DK-test");
        credential.setSecretCiphertext(cipher.encrypt(SECRET));
        credential.setEnabled(1);

        boundDevice = new Device();
        boundDevice.setId(BOUND_DEVICE_ID);
        boundDevice.setDeviceNo(BOUND_DEVICE_NO);
        when(credentialMapper.selectOne(any())).thenReturn(credential);
        when(deviceMapper.selectById(BOUND_DEVICE_ID)).thenReturn(boundDevice);
    }

    @Test
    void rawPayloadWithValidSignaturePasses() {
        String body = "{\"deviceNo\":\"EQ-1\"}";
        String signature = DeviceSignatureVerifier.hmacHex(SECRET, body);

        assertDoesNotThrow(() -> verifier.verifyRawPayload("DK-test", signature, body, BOUND_DEVICE_NO));
        verify(credentialMapper).updateById(credential);
        assertNotNull(credential.getLastUsedAt());
    }

    @Test
    void rawPayloadWithWrongSignatureRejected() {
        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", "deadbeef", "{}", BOUND_DEVICE_NO));
        assertEquals(401, e.getStatus());
        verify(deviceMapper, never()).selectById(any());
    }

    @Test
    void rawPayloadSignedForOtherDeviceRejectedWith403() {
        String body = "{\"deviceNo\":\"EQ-2\",\"temperature\":99.9}";
        String signature = DeviceSignatureVerifier.hmacHex(SECRET, body);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", signature, body, "EQ-2"));
        assertEquals(403, e.getStatus());
        assertEquals("凭证与设备编号不匹配", e.getMessage());
        verify(credentialMapper, never()).updateById(any(DeviceCredential.class));
    }

    @Test
    void rawPayloadWithoutDeviceNoRejectedWith403() {
        String body = "{}";
        String signature = DeviceSignatureVerifier.hmacHex(SECRET, body);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", signature, body, null));
        assertEquals(403, e.getStatus());
    }

    @Test
    void rawPayloadWhenBoundDeviceDeletedRejectedWith403() {
        when(deviceMapper.selectById(BOUND_DEVICE_ID)).thenReturn(null);
        String body = "{\"deviceNo\":\"EQ-1\"}";
        String signature = DeviceSignatureVerifier.hmacHex(SECRET, body);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", signature, body, BOUND_DEVICE_NO));
        assertEquals(403, e.getStatus());
        verify(credentialMapper, never()).updateById(any(DeviceCredential.class));
    }

    @Test
    void missingCredentialRejectedWith401() {
        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload(null, "any", "{}", BOUND_DEVICE_NO));
        assertEquals(401, e.getStatus());
    }

    @Test
    void disabledCredentialRejectedWith403() {
        credential.setEnabled(0);

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyRawPayload("DK-test", "any", "{}", BOUND_DEVICE_NO));
        assertEquals(403, e.getStatus());
        verify(deviceMapper, never()).selectById(any());
    }

    @Test
    void mqttReportWithValidSignaturePasses() {
        DeviceGatewayReport report = new DeviceGatewayReport(BOUND_DEVICE_NO, "RUNNING", 45.2, 1500.0, 8.5,
                "DK-test", Instant.now().toEpochMilli(), "n-1", null, "m-1");

        assertDoesNotThrow(() -> verifier.verifyReport(signed(report)));
        verify(credentialMapper).updateById(credential);
    }

    @Test
    void mqttReportSignedForOtherDeviceRejectedWith403() {
        DeviceGatewayReport report = new DeviceGatewayReport("EQ-2", "RUNNING", 99.9, 1500.0, 8.5,
                "DK-test", Instant.now().toEpochMilli(), "n-1", null, "m-1");

        DeviceAuthException e = assertThrows(DeviceAuthException.class,
                () -> verifier.verifyReport(signed(report)));
        assertEquals(403, e.getStatus());
        assertEquals("凭证与设备编号不匹配", e.getMessage());
        verify(credentialMapper, never()).updateById(any(DeviceCredential.class));
    }

    @Test
    void mqttReportOutsideToleranceRejected() {
        long stale = Instant.now().minusSeconds(600).toEpochMilli();
        DeviceGatewayReport report = new DeviceGatewayReport(BOUND_DEVICE_NO, "RUNNING", 45.2, 1500.0, 8.5,
                "DK-test", stale, "n-1", "whatever", "m-1");

        DeviceAuthException e = assertThrows(DeviceAuthException.class, () -> verifier.verifyReport(report));
        assertEquals(401, e.getStatus());
    }

    @Test
    void enabledWithoutMasterKeyFailsAtStartup() {
        DeviceSignatureVerifier misconfigured = new DeviceSignatureVerifier(
                credentialMapper, deviceMapper, new DeviceSecretCipher(""));
        ReflectionTestUtils.setField(misconfigured, "enabled", true);
        ReflectionTestUtils.setField(misconfigured, "toleranceSeconds", 300L);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                misconfigured::validateConfiguration);
        assertTrue(e.getMessage().contains("DEVICE_CREDENTIAL_KEY"));
    }

    @Test
    void disabledWithoutMasterKeyStartsNormally() {
        DeviceSignatureVerifier disabled = new DeviceSignatureVerifier(
                credentialMapper, deviceMapper, new DeviceSecretCipher(""));

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

    /**
     * 用本机凭证密钥对上报内容签名，模拟设备侧行为。
     *
     * @param report 上报内容
     * @return 带合法签名的上报
     */
    private DeviceGatewayReport signed(DeviceGatewayReport report) {
        return new DeviceGatewayReport(report.deviceNo(), report.status(), report.temperature(),
                report.rpm(), report.current(), report.keyId(), report.timestamp(), report.nonce(),
                DeviceSignatureVerifier.hmacHex(SECRET, DeviceSignatureVerifier.canonicalReport(report)),
                report.messageId());
    }
}
