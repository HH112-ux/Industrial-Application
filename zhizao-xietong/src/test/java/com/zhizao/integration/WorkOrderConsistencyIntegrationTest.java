package com.zhizao.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.ScheduleEntry;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.mapper.ScheduleEntryMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import com.zhizao.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单一致性的薄集成测试：真实 MySQL 上验证状态流转与乐观锁并发保护。
 *
 * <p>只覆盖两条最容易回归的路径：状态机的合法/非法流转、两个请求同时开工。
 * 本机未安装 Docker 时整类跳过，不影响其余单元测试。</p>
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass"
})
@Testcontainers(disabledWithoutDocker = true)
class WorkOrderConsistencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private WorkOrderMapper workOrderMapper;
    @Autowired
    private WorkOrderStatusHistoryMapper historyMapper;
    @Autowired
    private ScheduleEntryMapper scheduleEntryMapper;
    @Autowired
    private WorkOrderReportMapper workOrderReportMapper;

    /**
     * 插入一张指定状态的工单，工艺取种子数据中已发布的 1 号工艺。
     *
     * @param status 初始状态
     * @return 工单主键
     */
    private Long createOrder(String status) {
        WorkOrder order = new WorkOrder();
        order.setOrderNo("IT-" + System.nanoTime());
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
        order.setStatus(status);
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
        entry.setDurationHours(new java.math.BigDecimal("2"));
        entry.setEstimated(0);
        entry.setQueueRank(1);
        entry.setRankBasis("集成测试");
        entry.setStatus("SCHEDULED");
        entry.setMessage("集成测试排程");
        scheduleEntryMapper.insert(entry);
        return order.getId();
    }

    /**
     * 读取工单的状态流转历史条数。
     *
     * @param orderId 工单主键
     * @return 历史条数
     */
    private long historyCount(Long orderId) {
        return historyMapper.selectCount(new LambdaQueryWrapper<WorkOrderStatusHistory>()
                .eq(WorkOrderStatusHistory::getOrderId, orderId));
    }

    @Test
    void legalTransitionPersistsStatusAndHistory() {
        Long id = createOrder("PENDING");

        WorkOrder result = workOrderService.start(id, "tester");

        assertEquals("IN_PRODUCTION", result.getStatus());
        assertEquals(1, result.getVersion());
        assertEquals(1, historyCount(id));
        WorkOrderStatusHistory history = historyMapper.selectList(new LambdaQueryWrapper<WorkOrderStatusHistory>()
                .eq(WorkOrderStatusHistory::getOrderId, id)).get(0);
        assertEquals("PENDING", history.getFromStatus());
        assertEquals("IN_PRODUCTION", history.getToStatus());
        assertEquals("tester", history.getOperator());
    }

    @Test
    void illegalTransitionIsRejectedWithoutHistory() {
        Long id = createOrder("FINISHED");

        assertInstanceOf(BusinessException.class, captureStartFailure(id));
        assertEquals("FINISHED", workOrderMapper.selectById(id).getStatus());
        assertEquals(0, historyCount(id));
    }

    @Test
    void concurrentStartOnlyAdvancesOnce() throws Exception {
        Long id = createOrder("PENDING");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    gate.await(5, TimeUnit.SECONDS);
                    try {
                        workOrderService.start(id, "tester");
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            gate.countDown();
            int success = 0;
            int failure = 0;
            for (Future<Throwable> future : futures) {
                Throwable error = future.get(10, TimeUnit.SECONDS);
                if (error == null) {
                    success++;
                } else {
                    failure++;
                    assertInstanceOf(BusinessException.class, error, "失败方应是业务冲突，而不是未捕获的唯一键异常");
                    assertEquals(409, ((BusinessException) error).getCode(),
                            "并发开工失败（含 uk_work_order_mat 冲突）必须映射为业务码 409");
                }
            }
            assertEquals(1, success, "并发开工只能有一个成功");
            assertEquals(1, failure, "另一个请求必须被拒绝");
        } finally {
            pool.shutdownNow();
        }
        WorkOrder stored = workOrderMapper.selectById(id);
        assertEquals("IN_PRODUCTION", stored.getStatus());
        assertEquals(1, stored.getVersion());
        assertEquals(1, historyCount(id), "并发开工只能写一条历史");
    }

    /**
     * 调用开工并返回失败异常，未失败时让断言失败。
     *
     * @param id 工单主键
     * @return 开工抛出的异常
     */
    private Throwable captureStartFailure(Long id) {
        try {
            workOrderService.start(id, "tester");
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("非法流转应被拒绝");
    }

    @Test
    void fullReportThenQcFailureCanReworkAndFinish() {
        Long id = createOrder("PENDING");

        workOrderService.start(id, "tester");
        workOrderService.report(id, new ReportRequest(10, 10, 0, null, "rework-1"), "tester");
        WorkOrder failed = workOrderService.qc(id, new QcRequest("FAIL", "尺寸超差"), "qa");

        assertEquals("IN_PRODUCTION", failed.getStatus());
        assertEquals(10, failed.getQtyRework());

        WorkOrder reReported = workOrderService.report(id,
                new ReportRequest(10, 10, 0, null, "rework-2"), "tester");
        assertEquals("PENDING_QC", reReported.getStatus());

        WorkOrder finished = workOrderService.qc(id, new QcRequest("PASS", null), "qa");

        assertEquals("FINISHED", finished.getStatus());
        WorkOrder stored = workOrderMapper.selectById(id);
        assertEquals(20, stored.getQtyDone(), "累计报工数保留返工批次");
        assertEquals(10, stored.getQtyRework());
    }

    @Test
    void partialReportsAccumulateUntilFullThenQcFailReleasesWholeBatch() {
        Long id = createOrder("PENDING");
        workOrderService.start(id, "tester");

        WorkOrder first = workOrderService.report(id,
                new ReportRequest(4, 4, 0, null, "cumulative-1"), "tester");
        assertEquals("IN_PRODUCTION", first.getStatus(), "未报满不得离开生产中");
        assertEquals(4, first.getQtyDone());

        WorkOrder second = workOrderService.report(id,
                new ReportRequest(6, 6, 0, null, "cumulative-2"), "tester");
        assertEquals("PENDING_QC", second.getStatus(), "报满才转待质检");
        assertEquals(10, second.getQtyDone());

        WorkOrder failed = workOrderService.qc(id, new QcRequest("FAIL", "尺寸超差"), "qa");
        assertEquals("IN_PRODUCTION", failed.getStatus());
        assertEquals(10, failed.getQtyRework(), "返工额度应等于本轮两批报工之和");

        WorkOrder stored = workOrderMapper.selectById(id);
        int remaining = stored.getQty() + stored.getQtyRework() - stored.getQtyDone();
        assertEquals(stored.getQty(), remaining, "释放后剩余可报数不得大于计划数量");
        assertEquals(2, reportCount(id));
        assertEquals(4, historyCount(id), "开工、两次报工、一次质检各一条历史");
    }

    @Test
    void concurrentReportsCannotDoubleCountOrTransitionTwice() throws Exception {
        Long id = createOrder("PENDING");
        workOrderService.start(id, "tester");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                String key = "concurrent-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    gate.await(5, TimeUnit.SECONDS);
                    try {
                        workOrderService.report(id, new ReportRequest(10, 10, 0, null, key), "tester");
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            gate.countDown();
            int success = 0;
            int failure = 0;
            for (Future<Throwable> future : futures) {
                Throwable error = future.get(10, TimeUnit.SECONDS);
                if (error == null) {
                    success++;
                } else {
                    failure++;
                    assertInstanceOf(BusinessException.class, error);
                    int code = ((BusinessException) error).getCode();
                    assertTrue(code == 409 || error.getMessage().contains("仅「生产中」的工单可报工"),
                            "失败方应命中乐观锁冲突 409，或在首个请求提交后命中状态守卫");
                }
            }
            assertEquals(1, success, "同时报满只能有一个请求成功");
            assertEquals(1, failure);
        } finally {
            pool.shutdownNow();
        }

        WorkOrder stored = workOrderMapper.selectById(id);
        assertEquals("PENDING_QC", stored.getStatus());
        assertEquals(10, stored.getQtyDone(), "并发报工不得重复累计");
        assertEquals(1, reportCount(id), "失败请求的报工记录必须随事务回滚");
        assertEquals(2, historyCount(id), "开工与报满各一条历史，不得写两遍转移");
        long transitions = historyMapper.selectCount(new LambdaQueryWrapper<WorkOrderStatusHistory>()
                .eq(WorkOrderStatusHistory::getOrderId, id)
                .eq(WorkOrderStatusHistory::getToStatus, "PENDING_QC"));
        assertEquals(1, transitions, "转待质检只能发生一次");
        assertTrue(stored.getQtyDone() <= stored.getQty() + stored.getQtyRework());
    }

    /**
     * 统计工单的报工记录数。
     *
     * @param orderId 工单主键
     * @return 报工记录数
     */
    private long reportCount(Long orderId) {
        return workOrderReportMapper.selectCount(new LambdaQueryWrapper<WorkOrderReport>()
                .eq(WorkOrderReport::getOrderId, orderId));
    }
}
