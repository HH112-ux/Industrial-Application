package com.zhizao.service;

import com.zhizao.config.WeComNotifyProperties;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 企微通知测试：开关与配置缺失静默跳过、token 缓存复用、事务提交后投递、失败只记审计。
 */
class WeComAlarmNotifierTest {

    private static final String TOKEN_REQUEST =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp&corpsecret=secret";
    private static final String SEND_REQUEST =
            "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=token-1";
    private static final String TOKEN_BODY =
            "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"token-1\",\"expires_in\":7200}";
    private static final String OK_BODY = "{\"errcode\":0,\"errmsg\":\"ok\"}";

    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final Executor direct = Runnable::run;
    private MockRestServiceServer server;

    private AlarmRecord record() {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(1L);
        record.setRuleType("THRESHOLD");
        record.setMetric("temperature");
        record.setTriggerValue(90.0);
        record.setThresholdSnapshot("upper=80.0, lower=-");
        return record;
    }

    private WeComNotifyProperties properties(boolean enabled) {
        WeComNotifyProperties properties = new WeComNotifyProperties();
        properties.setEnabled(enabled);
        properties.setCorpId("corp");
        properties.setAgentId("1000002");
        properties.setSecret("secret");
        return properties;
    }

    private WeComAlarmNotifier notifier(WeComNotifyProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new WeComAlarmNotifier(builder.build(), auditLogMapper, properties, direct);
    }

    @Test
    void disabledOrUnconfiguredSkipsSilently() {
        WeComAlarmNotifier disabled = new WeComAlarmNotifier(
                RestClient.builder(), auditLogMapper, properties(false));
        assertDoesNotThrow(() -> disabled.notify(record()));

        WeComNotifyProperties unconfigured = properties(true);
        unconfigured.setSecret("");
        WeComAlarmNotifier notifier = notifier(unconfigured);
        assertDoesNotThrow(() -> notifier.notify(record()));
        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    void cachesAccessTokenAcrossNotifications() {
        WeComAlarmNotifier notifier = notifier(properties(true));
        server.expect(requestTo(TOKEN_REQUEST)).andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        notifier.notify(record());
        notifier.notify(record());

        // token 只取一次，第二条通知复用缓存
        server.verify();
    }

    @Test
    void refreshesTokenOnceWhenAccessTokenInvalid() {
        WeComAlarmNotifier notifier = notifier(properties(true));
        server.expect(requestTo(TOKEN_REQUEST)).andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST)).andRespond(withSuccess(
                "{\"errcode\":40014,\"errmsg\":\"invalid access_token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_REQUEST)).andRespond(withSuccess(
                "{\"errcode\":0,\"access_token\":\"token-2\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST.replace("token-1", "token-2")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        notifier.notify(record());

        server.verify();
    }

    @Test
    void sendsOnlyAfterTransactionCommit() {
        WeComAlarmNotifier notifier = notifier(properties(true));
        server.expect(requestTo(TOKEN_REQUEST)).andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        TransactionSynchronizationManager.initSynchronization();
        try {
            notifier.notify(record());
            assertThrows(AssertionError.class, server::verify);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        server.verify();
    }

    @Test
    void sendFailureIsAuditedAndNeverThrows() {
        WeComAlarmNotifier notifier = notifier(properties(true));
        server.expect(requestTo(TOKEN_REQUEST)).andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_REQUEST)).andRespond(withServerError());

        assertDoesNotThrow(() -> notifier.notify(record()));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertFalse(captor.getValue().getSuccess());
    }
}
