package com.zhizao.dto;

/**
 * 业务指标概览。分母为零的指标返回 {@code null} 和原因，不用 0 或 100% 伪装。
 *
 * @param devicesOnline          在线设备数
 * @param devicesTotal           设备总数
 * @param deviceOnlineRate       设备在线率，无设备时未定义
 * @param telemetryReports       已接受遥测上报数
 * @param telemetryIngestAvgMs   遥测上报平均处理耗时（毫秒），无上报时未定义
 * @param workOrderReports       成功报工次数
 * @param workOrderTaktPerHour   最近 24 小时工单节拍（合格件/小时）
 * @param materialKitRate        待开工工单齐套率，无适用工单时未定义
 * @author Caspian
 */
public record MetricsOverview(long devicesOnline,
                              long devicesTotal,
                              MetricValue deviceOnlineRate,
                              long telemetryReports,
                              MetricValue telemetryIngestAvgMs,
                              long workOrderReports,
                              MetricValue workOrderTaktPerHour,
                              MetricValue materialKitRate) {

    /**
     * 单项指标值。
     *
     * @param value  指标值，未定义时为 {@code null}
     * @param reason 未定义原因，有值时为空
     */
    public record MetricValue(Double value, String reason) {

        /**
         * 构造有值指标。
         *
         * @param value 指标值
         * @return 指标值
         */
        public static MetricValue of(double value) {
            return new MetricValue(value, null);
        }

        /**
         * 构造未定义指标。
         *
         * @param reason 未定义原因
         * @return 指标值
         */
        public static MetricValue undefined(String reason) {
            return new MetricValue(null, reason);
        }
    }
}
