package com.zhizao.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.dto.PasswordChangeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditAspectTest {

    @Test
    void passwordFieldsAreMaskedBeforePersisting() {
        AuditAspect aspect = new AuditAspect(null, new ObjectMapper());

        String json = aspect.serializeArgs(new Object[]{
                new PasswordChangeRequest("old-secret", "new-secret")});

        assertFalse(json.contains("old-secret"));
        assertFalse(json.contains("new-secret"));
        assertTrue(json.contains("***"));
    }
}
