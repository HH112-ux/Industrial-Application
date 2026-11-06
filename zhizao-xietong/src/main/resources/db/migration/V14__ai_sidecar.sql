-- S8 AI 旁路：建议留痕、查询审计与只读视图。
-- 只记录场景、目标与摘要，不保存 prompt 原文；查询审计只保存问题摘要与生成的 SQL。

CREATE TABLE IF NOT EXISTS ai_suggestion_log
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scene              VARCHAR(32)  NOT NULL COMMENT '场景 DEVICE_ISSUE/REPORT_DRAFT',
    username           VARCHAR(32)  DEFAULT NULL COMMENT '发起人',
    target_ref         VARCHAR(64)  DEFAULT NULL COMMENT '设备 ID 或工单 ID',
    evidence_count     INT          NOT NULL DEFAULT 0 COMMENT '输入证据条数',
    confidence         DECIMAL(4, 3) DEFAULT NULL COMMENT '模型置信度 0-1',
    suggestion_summary VARCHAR(500) DEFAULT NULL COMMENT '建议摘要，不保存 prompt 原文',
    available          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否产出建议 1 是 0 否',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scene_time (scene, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 建议留痕';

CREATE TABLE IF NOT EXISTS ai_query_audit
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(32)  DEFAULT NULL COMMENT '发起人',
    question_digest CHAR(64)     DEFAULT NULL COMMENT '问题摘要 SHA-256，不保存原文',
    sql_text        TEXT         DEFAULT NULL COMMENT '执行前的只读 SQL',
    row_count       INT          NOT NULL DEFAULT 0 COMMENT '返回行数',
    duration_ms     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    success         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功',
    error_code      VARCHAR(64)  DEFAULT NULL COMMENT '失败类型，不记录原始异常消息',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_time (created_at),
    KEY idx_user (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 数据问答审计';

-- 只读视图：Text-to-SQL 只允许访问以下视图，模型无法直接读写业务表。
CREATE OR REPLACE VIEW v_work_order_summary AS
SELECT w.id,
       w.order_no,
       w.plan_id,
       w.product_code,
       w.product_name,
       w.qty,
       w.qty_done,
       w.qty_qualified,
       w.qty_defect,
       w.batch,
       w.workshop,
       w.device_id,
       w.status,
       w.created_at,
       w.updated_at
FROM wo_work_order w
WHERE w.deleted = 0;

CREATE OR REPLACE VIEW v_device_status AS
SELECT d.id,
       d.device_no,
       d.device_name,
       d.device_type,
       d.manufacturer,
       d.model,
       d.status,
       d.workshop,
       d.last_seen_at
FROM eq_device d
WHERE d.deleted = 0;

CREATE OR REPLACE VIEW v_quality_summary AS
SELECT q.id,
       q.quality_no,
       q.order_id,
       w.order_no,
       w.product_name,
       w.workshop,
       q.inspector,
       q.result,
       q.defect_desc,
       q.inspect_time
FROM wo_quality q
         JOIN wo_work_order w ON w.id = q.order_id AND w.deleted = 0
WHERE q.deleted = 0;

CREATE OR REPLACE VIEW v_plan_progress AS
SELECT p.id,
       p.plan_order_no,
       p.product_code,
       p.product_name,
       p.plan_qty,
       p.plan_start,
       p.plan_end,
       p.priority,
       p.workshop,
       p.status,
       COALESCE(SUM(w.qty_done), 0) AS done_qty,
       COUNT(w.id)                  AS work_order_count
FROM po_plan p
         LEFT JOIN wo_work_order w ON w.plan_id = p.id AND w.deleted = 0
WHERE p.deleted = 0
GROUP BY p.id, p.plan_order_no, p.product_code, p.product_name, p.plan_qty,
         p.plan_start, p.plan_end, p.priority, p.workshop, p.status;

CREATE OR REPLACE VIEW v_kit_status AS
SELECT m.mat_code,
       m.mat_name,
       m.mat_type,
       m.stock_qty,
       m.safe_stock,
       m.kit_status,
       CASE WHEN m.stock_qty < m.safe_stock THEN 1 ELSE 0 END AS below_safe
FROM mt_material m
WHERE m.deleted = 0;
