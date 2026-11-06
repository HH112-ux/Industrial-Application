-- 设备遥测时序表：保存每次真实上报的数值，供历史查询与后续分析使用。
CREATE TABLE eq_telemetry
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id    BIGINT       NOT NULL COMMENT '设备主键',
    device_no    VARCHAR(32)  NOT NULL COMMENT '设备编号冗余，便于按编号导出与排障',
    status       VARCHAR(16)  DEFAULT NULL COMMENT '上报时的设备状态',
    temperature  DECIMAL(6, 2) DEFAULT NULL COMMENT '温度',
    rpm          INT          DEFAULT NULL COMMENT '转速',
    current      DECIMAL(6, 2) DEFAULT NULL COMMENT '电流',
    reported_at  DATETIME(3)  NOT NULL COMMENT '设备侧上报时间',
    received_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '服务端接收时间',
    ingest_key   VARCHAR(64)  DEFAULT NULL COMMENT '上报幂等键，为空表示不去重',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MQTT' COMMENT '来源：MQTT/HTTP/SIMULATED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingest_key (ingest_key),
    KEY idx_device_time (device_id, reported_at),
    KEY idx_device_no_time (device_no, reported_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备遥测时序';
