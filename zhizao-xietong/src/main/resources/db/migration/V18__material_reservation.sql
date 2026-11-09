-- 物料预占：工单开工时按 BOM 需求量占用库存，
-- 齐套判断改为「可用量 = 库存 − 已预占」，避免多张工单同时用同一批库存通过齐套。
CREATE TABLE IF NOT EXISTS mt_material_reservation (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT      NOT NULL COMMENT '占用工单 ID',
    mat_code      VARCHAR(32) NOT NULL COMMENT '物料编码',
    reserved_qty  INT         NOT NULL COMMENT '预占数量',
    status        VARCHAR(16) NOT NULL DEFAULT 'RESERVED' COMMENT '预占状态 RESERVED/CONSUMED',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_mat (work_order_id, mat_code),
    KEY idx_mat_status (mat_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料预占表';
