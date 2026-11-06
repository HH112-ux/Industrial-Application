-- S6 身份与数据域：用户启停、令牌版本、车间归属
ALTER TABLE sys_user
    ADD COLUMN enabled        TINYINT     NOT NULL DEFAULT 1 COMMENT '启用状态 1 启用 0 禁用',
    ADD COLUMN token_version  INT         NOT NULL DEFAULT 0 COMMENT '令牌版本，改密或启停时递增',
    ADD COLUMN workshop_scope VARCHAR(32) DEFAULT NULL COMMENT '车间数据域，NULL 表示不限制（管理员）';

-- 非管理员默认归属一车间，避免迁移后看不到存量数据
UPDATE sys_user SET workshop_scope = '一车间' WHERE role <> 'ADMIN';

ALTER TABLE eq_device
    ADD COLUMN workshop VARCHAR(32) NOT NULL DEFAULT '一车间' COMMENT '所属车间';
CREATE INDEX idx_device_workshop ON eq_device (workshop);

ALTER TABLE po_plan
    ADD COLUMN workshop VARCHAR(32) NOT NULL DEFAULT '一车间' COMMENT '所属车间';
CREATE INDEX idx_plan_workshop ON po_plan (workshop);
