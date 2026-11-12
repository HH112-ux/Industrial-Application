-- 计划下达幂等兜底：一个计划只允许生成一张工单，防止双击/请求重试并发下达产生重复工单。
-- 选取可空 dispatch_token 而不是直接给 plan_id 加唯一键，原因有二：
--   1) 存量数据若已因本缺陷产生同一计划的重复工单，直接加 uk(plan_id) 会让迁移失败；
--   2) 工单是逻辑删除（deleted=1 的行仍然留在表里），uk(plan_id) 会把已删工单也算进键位。
-- 可空列在 MySQL 唯一索引下允许多个 NULL，历史工单保持 NULL 即可，无需清洗存量数据。
-- 幂等性：ADD COLUMN / ADD UNIQUE KEY 不支持 IF NOT EXISTS，重复执行会报错，与仓库既有 ALTER 迁移口径一致（Flyway 只执行一次）。
-- 存量数据影响：仅新增列与索引，不回填、不修改既有行；历史工单 dispatch_token 为 NULL。
-- 回滚：
--   ALTER TABLE wo_work_order DROP INDEX uk_work_order_dispatch_token;
--   ALTER TABLE wo_work_order DROP COLUMN dispatch_token;
ALTER TABLE wo_work_order
    ADD COLUMN dispatch_token VARCHAR(64) DEFAULT NULL COMMENT '派工幂等键 PLAN-{planId}，历史数据为空' AFTER plan_id,
    ADD UNIQUE KEY uk_work_order_dispatch_token (dispatch_token);
