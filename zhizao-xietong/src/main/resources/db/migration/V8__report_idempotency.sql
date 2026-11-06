-- 报工幂等：移动端弱网重试复用同一幂等键，避免重复报工与重复累加。
-- 键由客户端生成（UUID），同工单内唯一；NULL 表示调用方未提供，不参与去重。
ALTER TABLE wo_report
    ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '客户端生成的幂等键，重试沿用' AFTER report_no,
    ADD UNIQUE KEY uk_order_idem (order_id, idempotency_key);
