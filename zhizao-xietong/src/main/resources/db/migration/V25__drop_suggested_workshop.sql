-- 删除死字段 suggested_workshop：写入路径从未赋值（恒为 NULL），也不存在部门到车间的映射配置，
-- 保留只会让读者误以为「部门推导车间」的功能已经存在。
-- department_ids 快照保留，管理员仍可据此人工核对部门。
-- 回滚：ALTER TABLE sys_user_identity
--         ADD COLUMN suggested_workshop VARCHAR(32) DEFAULT NULL COMMENT '部门推导的车间建议值，非授权值',
--         ADD KEY idx_suggested_workshop (suggested_workshop);
ALTER TABLE sys_user_identity DROP INDEX idx_suggested_workshop;
ALTER TABLE sys_user_identity DROP COLUMN suggested_workshop;
