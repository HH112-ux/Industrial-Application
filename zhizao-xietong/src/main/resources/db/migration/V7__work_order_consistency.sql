-- 工单执行一致性：乐观锁版本、累计数量、状态流转历史。
-- 历史表只追加，不更新、不删除，是状态机审计的事实来源。
ALTER TABLE wo_work_order
    ADD COLUMN version       INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER status,
    ADD COLUMN qty_done      INT NOT NULL DEFAULT 0 COMMENT '累计报工数量' AFTER qty,
    ADD COLUMN qty_qualified INT NOT NULL DEFAULT 0 COMMENT '累计合格数量' AFTER qty_done,
    ADD COLUMN qty_defect    INT NOT NULL DEFAULT 0 COMMENT '累计不良数量' AFTER qty_qualified;

CREATE TABLE wo_status_history
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT      NOT NULL COMMENT '工单主键',
    from_status VARCHAR(16) NOT NULL COMMENT '流转前状态',
    to_status   VARCHAR(16) NOT NULL COMMENT '流转后状态',
    operator    VARCHAR(32) DEFAULT NULL COMMENT '操作人，缺省 system',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '开工/报工/质检说明',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_order_time (order_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工单状态流转历史';
