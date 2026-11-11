-- 存量管理员强制改密标记：只新增列 + 有界 UPDATE，可回滚。
-- 幂等性：Flyway 按版本只执行一次；重复执行 ADD COLUMN 会失败，重放前需确认列不存在。
-- 存量数据影响：新列 NOT NULL DEFAULT 0，存量行取 0；随后仅把未删除的管理员置 1。
-- 回滚：UPDATE sys_user SET must_change_password = 0;
--       ALTER TABLE sys_user DROP COLUMN must_change_password;
ALTER TABLE sys_user
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '1=下次登录必须先改密';

UPDATE sys_user SET must_change_password = 1 WHERE role = 'ADMIN' AND deleted = 0;
