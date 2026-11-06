-- 工艺版本化：同一 process_no 的多行表示不同版本，每行自带审批状态、工序与 BOM。
ALTER TABLE pr_process
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '工艺版本号，同一 process_no 内递增' AFTER process_no,
    ADD UNIQUE KEY uk_process_no_version (process_no, version);

-- 工序路线：一个工艺版本下按 seq 有序执行。
CREATE TABLE IF NOT EXISTS pr_process_operation (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_id     BIGINT       NOT NULL COMMENT '工艺版本 ID，对应 pr_process.id',
    seq            INT          NOT NULL COMMENT '工序顺序，从 1 开始',
    operation_name VARCHAR(64)  NOT NULL COMMENT '工序名称',
    device_type    VARCHAR(64)  DEFAULT NULL COMMENT '所需设备类型',
    est_hours      DECIMAL(6,2) DEFAULT NULL COMMENT '单件标准工时（小时）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺工序表';

-- 存量工艺回填一条默认工序，保证版本化后仍能提交审批与排产。
INSERT INTO pr_process_operation (process_id, seq, operation_name)
SELECT id, 1, '默认工序' FROM pr_process WHERE deleted = 0;

-- 工单创建时锁定工艺版本，作为在制工单的追溯快照。
ALTER TABLE wo_work_order
    ADD COLUMN process_version INT DEFAULT NULL COMMENT '开工时锁定的工艺版本' AFTER process_id;

UPDATE wo_work_order wo
    JOIN pr_process p ON p.id = wo.process_id
SET wo.process_version = p.version;
