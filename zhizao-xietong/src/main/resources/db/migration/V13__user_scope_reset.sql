-- V11 曾把非管理员统一回填为「一车间」，但 sys_user 在 V11 之前没有任何车间归属字段，
-- 该回填没有确定依据，等于批量授予可见权。这里按 deny-by-default 纠正为 NULL：
-- 未显式分配车间的非管理员将看不到任何车间数据，由管理员通过
-- POST /api/users/{id}/workshop 显式分配。
-- 不修改已执行的 V11，保持 Flyway 前向迁移。
UPDATE sys_user SET workshop_scope = NULL WHERE role <> 'ADMIN';
