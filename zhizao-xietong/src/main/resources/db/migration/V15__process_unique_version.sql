-- V1 为 process_no 建立了单列唯一键，但工艺改为多版本后同一 process_no 需要有多行。
-- 版本唯一性已由 V9 的 uk_process_no_version (process_no, version) 保证，此处移除旧键。
-- 注意：MySQL 8 不支持 DROP INDEX IF EXISTS。若某环境曾手工删除该索引，
-- 本迁移会因索引不存在而失败，需人工确认后补执行或手工标记迁移完成。
ALTER TABLE pr_process DROP INDEX uk_process_no;
