-- 质检失败退回「生产中」后，被退回批次的数量需要允许重新报工。
-- 用独立额度记录，避免把 qty_done 从累计报工数改成合格数而改变既有语义。
ALTER TABLE wo_work_order
    ADD COLUMN qty_rework INT NOT NULL DEFAULT 0 COMMENT '质检失败批次释放的可返工额度' AFTER qty_defect;
