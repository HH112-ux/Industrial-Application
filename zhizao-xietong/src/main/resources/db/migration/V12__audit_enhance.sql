-- S6 审计查询：补按操作人与时间的组合索引，支撑管理端筛选
CREATE INDEX idx_audit_user_time ON sys_audit_log (username, created_at);
CREATE INDEX idx_audit_uri ON sys_audit_log (uri);
