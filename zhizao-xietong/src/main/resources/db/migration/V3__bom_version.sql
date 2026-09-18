-- BOM 版本管理：为 pr_bom 增加版本号列
ALTER TABLE pr_bom ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT 'BOM 版本号';
