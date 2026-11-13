package com.zhizao.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.ClientIpResolver;
import com.zhizao.dto.PasswordChangeRequest;
import com.zhizao.dto.UserCreateRequest;
import com.zhizao.dto.UserCreateResponse;
import com.zhizao.dto.UserView;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    @Test
    void passwordFieldsAreMaskedBeforePersisting() {
        AuditAspect aspect = new AuditAspect(null, new ObjectMapper(),
                new ClientIpResolver(""), transactionManager());

        String json = aspect.serializeArgs(new Object[]{
                new PasswordChangeRequest("old-secret", "new-secret")});

        assertFalse(json.contains("old-secret"));
        assertFalse(json.contains("new-secret"));
        assertTrue(json.contains("***"));
    }

    @Test
    void createAccountPasswordFieldsAreMaskedBeforePersisting() {
        AuditAspect aspect = new AuditAspect(null, new ObjectMapper(),
                new ClientIpResolver(""), transactionManager());
        UserView view = new UserView(7L, "wangwu", "王五", "USER", true, false, "一车间", "LOCAL", null, null);
        String json = aspect.serializeArgs(new Object[]{
                new UserCreateRequest("wangwu", "王五", "USER", "一车间", "given-secret"),
                new UserCreateResponse(view, "generated-secret")});
        assertFalse(json.contains("given-secret"));
        assertFalse(json.contains("generated-secret"));
    }

    @Test
    void recordsSuccessfulOperation() throws Throwable {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditAspect aspect = new AuditAspect(mapper, new ObjectMapper(),
                new ClientIpResolver(""), transactionManager());
        ProceedingJoinPoint pjp = joinPoint();
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.audit(pjp);

        assertEquals("ok", result);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertTrue(captor.getValue().getSuccess());
        assertNull(captor.getValue().getErrorMessage());
    }

    @Test
    void recordsFailedOperationWithErrorSummary() throws Throwable {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditAspect aspect = new AuditAspect(mapper, new ObjectMapper(),
                new ClientIpResolver(""), transactionManager());
        ProceedingJoinPoint pjp = joinPoint();
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> aspect.audit(pjp));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertFalse(captor.getValue().getSuccess());
        assertEquals("IllegalStateException: boom", captor.getValue().getErrorMessage());
    }

    @Test
    void auditFailureDoesNotBreakBusinessFlow() throws Throwable {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenThrow(new IllegalStateException("db down"));
        AuditAspect aspect = new AuditAspect(mapper, new ObjectMapper(),
                new ClientIpResolver(""), transactionManager());
        ProceedingJoinPoint pjp = joinPoint();
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.audit(pjp));
    }

    private static ProceedingJoinPoint joinPoint() {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn(AuditAspectTest.class);
        when(signature.getName()).thenReturn("sample");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        return pjp;
    }

    private static PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
