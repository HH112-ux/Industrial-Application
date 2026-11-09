-- 换型时间：同一设备连续生产不同产品时需要额外累积的切换工时（小时）。
-- 排程按「上一位产品不同」计一次换型，计入该工序占用的时间窗。
ALTER TABLE pr_process_operation
    ADD COLUMN setup_hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '同设备换型工时（小时）' AFTER est_hours;
