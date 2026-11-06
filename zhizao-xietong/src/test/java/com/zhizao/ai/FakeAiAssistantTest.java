package com.zhizao.ai;

import com.zhizao.config.AiProperties;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.ai.AiQueryContext;
import com.zhizao.dto.ai.DeviceIssueContext;
import com.zhizao.dto.ai.DeviceIssueSuggestion;
import com.zhizao.dto.ai.ReportDraft;
import com.zhizao.dto.ai.ReportDraftContext;
import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.entity.WorkOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAiAssistantTest {

    private final FakeAiAssistant assistant = new FakeAiAssistant();

    @Test
    void extractsReportQuantitiesWithoutNetwork() {
        WorkOrder order = order();
        ReportDraft draft = assistant.draftReport(new ReportDraftContext(order,
                "3 号机加工了 50 件，合格 48，不良 2，操作工：张三"));

        assertEquals(50, draft.qty());
        assertEquals(48, draft.qualifiedQty());
        assertEquals(2, draft.defectQty());
        assertEquals("张三", draft.operator());
        assertTrue(draft.missingFields().isEmpty());
        assertTrue(draft.requiresConfirmation());
    }

    @Test
    void keepsMissingFieldsNullInsteadOfGuessing() {
        ReportDraft draft = assistant.draftReport(new ReportDraftContext(order(), "今天加工了一些，合格不清楚"));

        assertNull(draft.qty());
        assertNull(draft.qualifiedQty());
        assertNull(draft.defectQty());
        assertTrue(draft.missingFields().containsAll(List.of("qty", "qualifiedQty", "defectQty")));
        assertTrue(draft.requiresConfirmation());
    }

    @Test
    void everyDeviceCauseCarriesEvidence() {
        DeviceIssueContext context = new DeviceIssueContext(device(), telemetry(), List.of(),
                List.of(maintenance()), List.of(), List.of(order()));

        DeviceIssueSuggestion suggestion = assistant.suggestDeviceIssue(context);

        assertFalse(suggestion.causes().isEmpty());
        assertTrue(suggestion.causes().stream().allMatch(cause ->
                cause.evidence() != null && !cause.evidence().isBlank()));
        assertFalse(suggestion.actions().isEmpty());
        assertTrue(suggestion.confidence() >= 0 && suggestion.confidence() <= 1);
        assertNotNull(suggestion.disclaimer());
    }

    @Test
    void generatedSqlStaysInsideReadOnlyWhitelist() {
        AiProperties properties = new AiProperties();
        properties.setMaxRows(200);
        ReadOnlySqlGuard guard = new ReadOnlySqlGuard(properties);
        for (String question : List.of("设备在线情况", "质量不良最多的工单", "计划进度", "物料齐套", "工单完成情况")) {
            String sql = assistant.proposeSql(new AiQueryContext(question));
            assertTrue(sql.toLowerCase().contains("select"));
            assertNotNull(guard.validate(sql));
        }
    }

    private WorkOrder order() {
        WorkOrder order = new WorkOrder();
        order.setId(9L);
        order.setOrderNo("WO-202611-0001");
        order.setProductName("齿轮");
        order.setQty(100);
        order.setQtyDone(40);
        order.setWorkshop("一车间");
        order.setStatus("IN_PRODUCTION");
        return order;
    }

    private Device device() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceNo("EQ-202611-0001");
        device.setDeviceName("数控机床");
        device.setDeviceType("CNC");
        device.setModel("X1");
        device.setStatus("STOPPED");
        device.setWorkshop("一车间");
        return device;
    }

    private DeviceTelemetry telemetry() {
        return new DeviceTelemetry("EQ-202611-0001", "数控机床", "STOPPED",
                80.0, 1200, 12.5, 0.4, LocalDateTime.now(), "HTTP");
    }

    private MaintenanceRecord maintenance() {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(11L);
        record.setDeviceId(1L);
        record.setMtType("REPAIR");
        record.setMtTime(LocalDateTime.now().minusDays(2));
        return record;
    }
}
