-- 有限产能排程结果：一次排程运行后，每张「待排」工单按工序落位的结果。
-- 该表是派生数据：重排时先删除「待排」工单的旧结果再重建；在产/已完工工单的结果保留，
-- 既作为已占用产能，也作为「为什么排在这个时间」的追溯依据。
CREATE TABLE IF NOT EXISTS po_schedule_entry (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id   BIGINT       NOT NULL COMMENT '工单 ID',
    plan_id         BIGINT       NOT NULL COMMENT '来源计划 ID',
    operation_seq   INT          DEFAULT NULL COMMENT '工序顺序，未绑定工序时为空',
    operation_name  VARCHAR(64)  DEFAULT NULL COMMENT '工序名称',
    device_type     VARCHAR(64)  DEFAULT NULL COMMENT '占用设备类型',
    device_id       BIGINT       DEFAULT NULL COMMENT '落位设备 ID',
    start_time      DATETIME     DEFAULT NULL COMMENT '占用起始时间',
    end_time        DATETIME     DEFAULT NULL COMMENT '占用结束时间',
    duration_hours  DECIMAL(8,2) DEFAULT NULL COMMENT '工序总工时（小时），跨天时为累计有效工时',
    estimated       TINYINT      NOT NULL DEFAULT 0 COMMENT '工时为估算值：1 是，0 否',
    queue_rank      INT          NOT NULL COMMENT '本次排程队列位置，从 1 开始',
    rank_basis      VARCHAR(255) NOT NULL COMMENT '队列位置依据（交期、优先级）',
    status          VARCHAR(16)  NOT NULL COMMENT '结果 SCHEDULED/UNSCHEDULED',
    conflict_code   VARCHAR(32)  DEFAULT NULL COMMENT '无法排入的业务码',
    message         VARCHAR(255) DEFAULT NULL COMMENT '可读原因或排程说明',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本次排程写入时间',
    PRIMARY KEY (id),
    KEY idx_work_order (work_order_id),
    KEY idx_device_time (device_id, start_time),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有限产能排程结果表';
