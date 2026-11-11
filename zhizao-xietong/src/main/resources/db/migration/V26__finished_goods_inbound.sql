-- 成品入库单：工单质检通过并完工后生成的待入库记录，覆盖实物流转的最后一环。
-- 幂等性：CREATE TABLE IF NOT EXISTS 可重复执行；uk_inbound_work_order 保证一张工单最多一条入库单。
-- 存量数据影响：新增表，不回填历史已完工工单，也不影响既有表。
-- 回滚：DROP TABLE fg_inbound_order;
CREATE TABLE IF NOT EXISTS fg_inbound_order (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT       NOT NULL COMMENT 'wo_work_order.id',
    product_code  VARCHAR(32)  NOT NULL COMMENT '成品编码，取自工单',
    qty           INT          NOT NULL COMMENT '待入库数量 = 合格数 - 已入库数',
    batch         VARCHAR(64)  NOT NULL COMMENT '批次号，默认工单号-日期，允许确认时覆盖',
    workshop      VARCHAR(32)  NOT NULL COMMENT '车间，用于数据域过滤',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/INBOUND/CANCELLED',
    operator      VARCHAR(64)  DEFAULT NULL COMMENT '确认或取消操作人',
    inbound_time  DATETIME     DEFAULT NULL COMMENT '确认入库时间',
    cancel_reason VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbound_work_order (work_order_id),
    KEY idx_inbound_workshop_status (workshop, status),
    KEY idx_inbound_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品入库单';
