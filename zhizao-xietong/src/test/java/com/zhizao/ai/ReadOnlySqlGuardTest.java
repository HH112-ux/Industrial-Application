package com.zhizao.ai;

import com.zhizao.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadOnlySqlGuardTest {

    private ReadOnlySqlGuard guard() {
        AiProperties properties = new AiProperties();
        properties.setMaxRows(200);
        return new ReadOnlySqlGuard(properties);
    }

    @Test
    void acceptsWhitelistedViewAndCapsRows() {
        String sql = guard().validate("SELECT order_no, status FROM v_work_order_summary");
        assertTrue(sql.contains("v_work_order_summary"));
        assertTrue(sql.endsWith("LIMIT 200"));
    }

    @Test
    void ignoresDmlWordsInsideStringLiterals() {
        assertDoesNotThrow(() -> guard().validate(
                "SELECT order_no FROM v_work_order_summary WHERE product_name = 'delete me'"));
    }

    @Test
    void rejectsWriteStatements() {
        assertThrows(SqlRejectedException.class, () -> guard().validate("UPDATE wo_work_order SET qty = 1"));
        assertThrows(SqlRejectedException.class, () -> guard().validate("DELETE FROM v_device_status"));
        assertThrows(SqlRejectedException.class, () -> guard().validate("DROP TABLE v_device_status"));
    }

    @Test
    void rejectsMultipleStatementsAndComments() {
        assertThrows(SqlRejectedException.class, () -> guard().validate(
                "SELECT * FROM v_device_status; DELETE FROM sys_user"));
        assertThrows(SqlRejectedException.class, () -> guard().validate(
                "SELECT * FROM v_device_status -- injected"));
        assertThrows(SqlRejectedException.class, () -> guard().validate(
                "SELECT /* injected */ * FROM v_device_status"));
    }

    @Test
    void rejectsNonWhitelistedAndSensitiveTables() {
        assertThrows(SqlRejectedException.class, () -> guard().validate("SELECT * FROM sys_user"));
        assertThrows(SqlRejectedException.class, () -> guard().validate("SELECT * FROM eq_device"));
        assertThrows(SqlRejectedException.class, () -> guard().validate(
                "SELECT * FROM v_device_status, sys_user"));
    }

    @Test
    void rejectsLockingReads() {
        assertThrows(SqlRejectedException.class, () -> guard().validate(
                "SELECT * FROM v_device_status FOR UPDATE"));
    }
}
