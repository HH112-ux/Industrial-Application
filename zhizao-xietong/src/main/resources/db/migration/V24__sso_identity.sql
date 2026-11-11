-- SSO 身份映射表：外部提供方 + 外部唯一标识 → 本地账号（设计稿 §2.5）
-- 幂等性：CREATE TABLE IF NOT EXISTS 可重复执行；下方 ADD COLUMN 不可重复执行，重放前需确认列不存在。
-- 存量数据影响：auth_source 默认 LOCAL，存量账号（含 admin）行为不变；不回填任何角色或车间。
-- 回滚：DROP TABLE sys_user_identity; ALTER TABLE sys_user DROP COLUMN auth_source;
CREATE TABLE IF NOT EXISTS sys_user_identity (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT       NOT NULL COMMENT 'sys_user.id',
    provider            VARCHAR(16)  NOT NULL COMMENT 'WEIXIN_WORK / FEISHU / DINGTALK',
    external_id         VARCHAR(64)  NOT NULL COMMENT '企微 userid，稳定外部主键',
    external_username   VARCHAR(64)  DEFAULT NULL COMMENT '外部登录名，仅展示',
    display_name        VARCHAR(64)  DEFAULT NULL COMMENT '姓名快照，仅展示',
    mobile              VARCHAR(32)  DEFAULT NULL COMMENT '手机号快照，仅展示',
    email               VARCHAR(64)  DEFAULT NULL COMMENT '邮箱快照，仅展示',
    department_ids      VARCHAR(255) DEFAULT NULL COMMENT '企微部门 ID 列表，仅作建议依据',
    suggested_workshop  VARCHAR(32)  DEFAULT NULL COMMENT '部门推导的车间建议值，非授权值',
    last_login_at       DATETIME     DEFAULT NULL,
    last_synced_at      DATETIME     DEFAULT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_external (provider, external_id),
    UNIQUE KEY uk_user_provider (user_id, provider),
    KEY idx_suggested_workshop (suggested_workshop)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部身份映射表';

-- 区分本地账号与 SSO 账号：SSO 账号禁止本地密码登录，避免含糊的“用户名或密码错误”
ALTER TABLE sys_user
    ADD COLUMN auth_source VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/SSO';
