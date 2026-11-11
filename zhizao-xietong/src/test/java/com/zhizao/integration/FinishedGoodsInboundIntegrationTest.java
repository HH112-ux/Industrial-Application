package com.zhizao.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.ScheduleEntry;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.FinishedGoodsInboundMapper;
import com.zhizao.mapper.ScheduleEntryMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.service.FinishedGoodsInboundService;
import com.zhizao.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 成品入库生成链路的集成测试：真实 MySQL 上验证合格数口径与工单维度幂等。
 *
 * <p>本机未安装 Docker 时整类跳过，不影响其余单元测试。</p>
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass"
})
@Testcontainers(disabledWithoutDocker = true)
class FinishedGoodsInboundIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private FinishedGoodsInboundService inboundService;
    @Autowired
    private WorkOrderMapper workOrderMapper;
    @Autowired
    private FinishedGoodsInboundMapper inboundMapper;
    @Autowired
    private ScheduleEntryMapper scheduleEntryMapper;

    /**
     * 插入一张计划数量为 10 的工单，并补一条排程记录供开工使用。
     *
     * @return 工单主键
     */
    private Long createOrder() {
        WorkOrder order = new WorkOrder();
        order.setOrderNo("FG-" + System.nanoTime());
        order.setPlanId(1L);
        order.setProductCode("P-001");
        order.setProductName("传动轴 A");
        order.setQty(10);
        order.setQtyDone(0);
        order.setQtyQualified(0);
        order.setQtyDefect(0);
        order.setBatch("B01");
        order.setWorkshop("一车间");
        order.setProcessId(1L);
        order.setStatus("PENDING");
        order.setVersion(0);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setDeleted(0);
        workOrderMapper.insert(order);
        ScheduleEntry entry = new ScheduleEntry();
        entry.setWorkOrderId(order.getId());
        entry.setPlanId(1L);
        entry.setOperationSeq(1);
        entry.setOperationName("下料");
        entry.setDeviceType("数控车床");
        entry.setDeviceId(1L);
        entry.setStartTime(LocalDateTime.now().minusHours(1));
        entry.setEndTime(LocalDateTime.now().plusHours(1));
        entry.setDurationHours(new BigDecimal("2"));
        entry.setEstimated(0);
        entry.setQueueRank(1);
        entry.setRankBasis("集成测试");
        entry.setStatus("SCHEDULED");
        entry.setMessage("集成测试排程");
        scheduleEntryMapper.insert(entry);
        return order.getId();
    }

    /** 读取该工单唯一的一条入库单，顺带断言工单维度唯一。 */
    private FinishedGoodsInbound onlyInbound(Long orderId) {
        List<FinishedGoodsInbound> records = inboundMapper.selectList(
                new LambdaQueryWrapper<FinishedGoodsInbound>()
                        .eq(FinishedGoodsInbound::getWorkOrderId, orderId));
        assertEquals(1, records.size(), "一张工单只能有一条入库单");
        return records.get(0);
    }

    @Test
    void reworkedBatchGeneratesInboundWithCurrentRoundQuantity() {
        Long id = createOrder();
        workOrderService.start(id, "tester");
        workOrderService.report(id, new ReportRequest(10, 10, 0, null, "rework-1"), "tester");
        workOrderService.qc(id, new QcRequest("FAIL", "尺寸超差"), "qa");
        workOrderService.report(id, new ReportRequest(10, 10, 0, null, "rework-2"), "tester");

        WorkOrder finished = workOrderService.qc(id, new QcRequest("PASS", null), "qa");

        assertEquals("FINISHED", finished.getStatus());
        assertEquals(20, workOrderMapper.selectById(id).getQtyDone(), "累计报工保留返工批次");
        FinishedGoodsInbound inbound = onlyInbound(id);
        assertEquals(10, inbound.getQty(), "返工重报后待入库数量应为 10，而不是 20");
        assertEquals("PENDING", inbound.getStatus());
    }

    @Test
    void partialReworkGeneratesOnlyReworkedQuantity() {
        Long id = createOrder();
        workOrderService.start(id, "tester");
        workOrderService.report(id, new ReportRequest(10, 10, 0, null, "partial-1"), "tester");
        workOrderService.qc(id, new QcRequest("FAIL", "尺寸超差"), "qa");
        // 状态机要求报满 qty+qtyRework 才能进入待质检，返工只报 5 无法通过公开接口构造，
        // 这里直接置为待质检并保留本轮的 5 件合格数，模拟部分返工的实际落库结果。
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getStatus, "PENDING_QC")
                .set(WorkOrder::getQtyDone, 15)
                .set(WorkOrder::getQtyQualified, 5));

        workOrderService.qc(id, new QcRequest("PASS", null), "qa");

        assertEquals(5, onlyInbound(id).getQty(), "部分返工只入库返工后的 5 件，不是 10 件");
    }

    @Test
    void defectiveQuantityIsExcludedFromInbound() {
        Long id = createOrder();
        workOrderService.start(id, "tester");
        workOrderService.report(id, new ReportRequest(10, 8, 2, null, "defect-1"), "tester");

        workOrderService.qc(id, new QcRequest("PASS", null), "qa");

        assertEquals(8, onlyInbound(id).getQty(), "不良品不计入成品入库，只入库合格的 8 件");
    }

    @Test
    void duplicateFinishTriggerDoesNotCreateSecondInbound() {
        Long id = createOrder();
        workOrderService.start(id, "tester");
        workOrderService.report(id, new ReportRequest(10, 10, 0, null, "dup-1"), "tester");
        workOrderService.qc(id, new QcRequest("PASS", null), "qa");

        inboundService.generateFor(workOrderMapper.selectById(id));

        assertEquals(1, inboundMapper.selectCount(new LambdaQueryWrapper<FinishedGoodsInbound>()
                .eq(FinishedGoodsInbound::getWorkOrderId, id)), "重复触发不得生成第二条入库单");
    }
}
