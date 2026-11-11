package com.zhizao.service;

import com.zhizao.entity.AuthEvent;
import com.zhizao.mapper.AuthEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 认证事件审计：把登录、登出、SSO 换票的结果写入 {@code sys_auth_event}。
 *
 * <p>独立事务写入，失败只记 WARN，不影响登录；调用方只传显式字段，不传请求体，
 * 口令与 SSO ticket/state 没有入库通道。</p>
 *
 * @author Caspian
 */
@Service
public class AuthAuditService {

    public static final String EVENT_LOGIN = "LOGIN";
    public static final String EVENT_LOGOUT = "LOGOUT";
    public static final String EVENT_SSO_LOGIN = "SSO_LOGIN";

    public static final String SUCCESS = "SUCCESS";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String SSO_REQUIRED = "SSO_REQUIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String INVALID_TICKET = "INVALID_TICKET";
    public static final String SSO_DISABLED = "SSO_DISABLED";
    public static final String SSO_REJECTED = "SSO_REJECTED";
    public static final String SSO_FAILED = "SSO_FAILED";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAuditService.class);
    private static final int USERNAME_MAX = 64;
    private static final int REASON_MAX = 255;

    private final AuthEventMapper authEventMapper;
    private final TransactionTemplate auditTransaction;

    /** 构造认证审计服务，写入使用独立事务（{@code REQUIRES_NEW}）。 */
    public AuthAuditService(AuthEventMapper authEventMapper, PlatformTransactionManager transactionManager) {
        this.authEventMapper = authEventMapper;
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 记录一条认证事件；写入失败只记 WARN，不向调用方抛异常。 */
    public void record(String eventType, String username, boolean success, String resultCode,
                       String reason, String ip) {
        AuthEvent event = new AuthEvent();
        event.setEventType(eventType);
        event.setUsername(truncate(username, USERNAME_MAX));
        event.setSuccess(success);
        event.setResultCode(resultCode);
        event.setReason(truncate(reason, REASON_MAX));
        event.setIp(truncate(ip, 64));
        event.setCreatedAt(LocalDateTime.now());
        try {
            auditTransaction.executeWithoutResult(status -> authEventMapper.insert(event));
        } catch (Exception e) {
            LOGGER.warn("认证审计写入失败 event={} username={} result={}", eventType, username, resultCode, e);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
