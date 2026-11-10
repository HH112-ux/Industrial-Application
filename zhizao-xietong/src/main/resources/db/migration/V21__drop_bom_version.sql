-- BOM 版本已由工艺版本行（process_id → pr_process.version）表达，
-- pr_bom.version 自 V9 起不再读写，此处删除该死列。
ALTER TABLE pr_bom DROP COLUMN version;
