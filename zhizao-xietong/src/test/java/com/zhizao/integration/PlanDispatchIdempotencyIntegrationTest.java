package com.zhizao.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.DispatchRequest;
import com.zhizao.entity.Plan;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划下达幂等的薄集成测试：真实 MySQL 上验证重复下达只产生一张工单。
 *
 * <p>只覆盖两条最容易回归的路径：两个请求同时下达、顺序重复下达。
 * 本机未安装 Docker 时整类跳过，不影响其余单元测试。</p>
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass",
        // 本用例依赖演示工艺；演示种子默认关闭，测试内显式开启
        "app.seed.demo-enabled=true"
})
@Testcontainers
class PlanDispatchIdempotencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private PlanService planService;
    @Autowired
    private PlanMapper planMapper;
    @Autowired
    private WorkOrderMapper workOrderMapper;

    /**
     * 插入一条待排产计划，工艺使用种子数据中已发布的 1 号工艺。
     *
     * @return 计划主键
     */
    private Long createPlan() {
        Plan plan = new Plan();
        plan.setPlanOrderNo("IT-PO-" + System.nanoTime());
        plan.setProductCode("P-001");
        plan.setProductName("传动轴 A");
        plan.setPlanQty(10);
        plan.setPlanStart(LocalDate.now());
        plan.setPlanEnd(LocalDate.now().plusDays(1));
        plan.setPriority("MEDIUM");
        plan.setBatch("B01");
        plan.setWorkshop("一车间");
        plan.setStatus("PENDING_SCHEDULE");
        planMapper.insert(plan);
        return plan.getId();
    }

    /**
     * 统计某计划下的工单数量（逻辑删除的工单不计入）。
     *
     * @param planId 计划主键
     * @return 工单数量
     */
    private long orderCount(Long planId) {
        return workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getPlanId, planId));
    }

    @Test
    void concurrentDispatchCreatesSingleWorkOrder() throws Exception {
        Long planId = createPlan();
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
                        planService.dispatch(planId, new DispatchRequest(1L, null, null));
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
                    BusinessException conflict = assertInstanceOf(BusinessException.class, error,
                            "失败方应是业务冲突，而不是未捕获的唯一键异常");
                    assertTrue(conflict.getCode() == 409 || conflict.getMessage().contains("仅「待排产」"),
                            "失败方必须是可读的业务拒绝，实际：code=" + conflict.getCode()
                                    + " message=" + conflict.getMessage());
                }
            }
            assertEquals(1, success, "并发下达只能有一个成功");
            assertEquals(1, failure, "另一个请求必须被拒绝");
            assertEquals(1, orderCount(planId), "同一计划只能存在一张工单");
            assertEquals("IN_PROGRESS", planMapper.selectById(planId).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateDispatchTokenIsRejectedByDatabase() {
        Long planId = createPlan();
        workOrderMapper.insert(orderFor(planId, "IT-WO-" + System.nanoTime()));

        WorkOrder duplicate = orderFor(planId, "IT-WO-" + System.nanoTime());

        assertThrows(DuplicateKeyException.class, () -> workOrderMapper.insert(duplicate),
                "V33 的唯一索引必须挡住同一计划的第二张工单");
        assertEquals(1, orderCount(planId));
    }

    /**
     * 直接构造一张带派工幂等键的工单，绕过服务层，用于验证数据库唯一索引。
     *
     * @param planId  计划主键
     * @param orderNo 工单号
     * @return 工单实体
     */
    private static WorkOrder orderFor(Long planId, String orderNo) {
        WorkOrder order = new WorkOrder();
        order.setOrderNo(orderNo);
        order.setPlanId(planId);
        order.setDispatchToken("PLAN-" + planId);
        order.setProductCode("P-001");
        order.setProductName("传动轴 A");
        order.setQty(1);
        order.setBatch("B01");
        order.setWorkshop("一车间");
        order.setStatus("PENDING");
        return order;
    }

    @Test
    void sequentialRedispatchIsRejectedWithoutSecondOrder() {
        Long planId = createPlan();

        WorkOrder order = planService.dispatch(planId, new DispatchRequest(1L, null, null));
        BusinessException error = assertThrows(BusinessException.class,
                () -> planService.dispatch(planId, new DispatchRequest(1L, null, null)));

        assertEquals("仅「待排产」的计划可下达工单", error.getMessage());
        assertEquals(1, orderCount(planId));
        assertEquals("PLAN-" + planId, workOrderMapper.selectById(order.getId()).getDispatchToken());
    }
}
