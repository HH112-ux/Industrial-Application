-- 设备报警记录：阈值/离线报警统一落库；仅新增表，不影响存量数据，可重复执行。
-- 回滚：DROP TABLE IF EXISTS eq_alarm_record;
CREATE TABLE IF NOT EXISTS eq_alarm_record
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id          BIGINT       NOT NULL COMMENT 'eq_device.id',
    metric             VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '指标：temperature/rpm/current；离线报警为空串',
    rule_type          VARCHAR(16)  NOT NULL COMMENT 'THRESHOLD/OFFLINE',
    trigger_value      DOUBLE                DEFAULT NULL COMMENT '最近一次触发值，未恢复期间持续刷新',
    threshold_snapshot VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建时的阈值快照',
    start_time         DATETIME     NOT NULL COMMENT '报警开始时间',
    recover_time       DATETIME              DEFAULT NULL COMMENT '恢复时间',
    status             VARCHAR(16)  NOT NULL COMMENT 'OPEN/ACKNOWLEDGED/RECOVERED',
    ack_user           VARCHAR(64)           DEFAULT NULL COMMENT '确认人',
    ack_time           DATETIME              DEFAULT NULL COMMENT '确认时间',
    active_flag        TINYINT               DEFAULT 1 COMMENT '未恢复为 1，恢复后置 NULL，配合唯一键去重',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alarm_active (device_id, rule_type, metric, active_flag),
    KEY idx_alarm_status_start (status, start_time),
    KEY idx_alarm_device_metric (device_id, metric)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备报警记录';
