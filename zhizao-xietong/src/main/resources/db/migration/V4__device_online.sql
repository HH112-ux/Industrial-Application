-- 设备在线判定：记录最近一次有效上报时间，供在线状态与离线告警使用
ALTER TABLE eq_device
    ADD COLUMN last_seen_at DATETIME(3) DEFAULT NULL COMMENT '最近一次设备上报时间',
    ADD KEY idx_last_seen (last_seen_at);
