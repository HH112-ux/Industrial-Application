-- 原唯一键只有 ingest_key，不同设备的自增 messageId 会互相撞键，导致历史遥测被静默丢弃。
-- 改为按设备隔离的唯一键 (device_id, ingest_key)。
-- 当前单列唯一键下不会存在同设备重复，以下清理语句为防御性处理：
-- 若曾有手工删键导致 (device_id, ingest_key) 重复，保留最早一条。
DELETE t1
FROM eq_telemetry t1
         JOIN eq_telemetry t2
              ON t1.device_id = t2.device_id
                  AND t1.ingest_key = t2.ingest_key
                  AND t1.ingest_key IS NOT NULL
                  AND t1.id > t2.id;

ALTER TABLE eq_telemetry
    DROP INDEX uk_ingest_key,
    ADD UNIQUE KEY uk_device_ingest_key (device_id, ingest_key);
