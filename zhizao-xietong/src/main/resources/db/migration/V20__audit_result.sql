-- 审计结果：记录失败操作，区分成功/失败并保留失败原因摘要
ALTER TABLE sys_audit_log
    ADD COLUMN success       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功，0 失败' AFTER ip,
    ADD COLUMN error_message VARCHAR(255) DEFAULT NULL COMMENT '失败原因摘要' AFTER success;
