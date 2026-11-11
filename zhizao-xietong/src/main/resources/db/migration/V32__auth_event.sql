-- 认证事件审计：登录、登出、SSO 换票的成功/失败留痕，与业务操作审计分表。
-- 只记录事件类型、结果码、原因摘要与来源 IP，不记录请求体，避免口令/授权码/state 入库。
-- 幂等性：CREATE TABLE IF NOT EXISTS 可重复执行。
-- 存量数据影响：新增表，不改动 sys_audit_log 及其历史迁移。
-- 回滚：DROP TABLE sys_auth_event;
CREATE TABLE IF NOT EXISTS sys_auth_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_type  VARCHAR(16)  NOT NULL COMMENT 'LOGIN / LOGOUT / SSO_LOGIN',
    username    VARCHAR(64)  DEFAULT NULL COMMENT '登录名，失败时取提交值',
    success     TINYINT(1)   NOT NULL COMMENT '是否成功：1 成功，0 失败',
    result_code VARCHAR(32)  NOT NULL COMMENT 'SUCCESS / BAD_CREDENTIALS / ACCOUNT_DISABLED / SSO_REQUIRED / RATE_LIMITED / INVALID_STATE / INVALID_TICKET / SSO_DISABLED / SSO_REJECTED / SSO_FAILED',
    reason      VARCHAR(255) DEFAULT NULL COMMENT '失败原因摘要',
    ip          VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_event_time (created_at),
    KEY idx_auth_event_user_time (username, created_at),
    KEY idx_auth_event_result (result_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证事件审计表';
