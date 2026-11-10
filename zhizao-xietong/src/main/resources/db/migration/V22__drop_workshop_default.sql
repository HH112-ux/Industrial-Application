-- 车间归属由应用层显式写入（新建计划、新建设备均必填），移除 V11 遗留的列默认值。
-- 只改 DEFAULT，保留 NOT NULL，也不触碰任何存量数据。
-- 回滚：ALTER TABLE eq_device ALTER COLUMN workshop SET DEFAULT '一车间';
--       ALTER TABLE po_plan   ALTER COLUMN workshop SET DEFAULT '一车间';
ALTER TABLE eq_device ALTER COLUMN workshop DROP DEFAULT;
ALTER TABLE po_plan ALTER COLUMN workshop DROP DEFAULT;
