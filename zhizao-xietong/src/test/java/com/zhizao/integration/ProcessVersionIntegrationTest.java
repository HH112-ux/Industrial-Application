package com.zhizao.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.Process;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.service.ProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 工艺版本复制的集成测试：在真实 MySQL 上验证唯一键与版本号推进。
 *
 * <p>覆盖两个曾导致版本复制必然失败的点：V1 遗留的 {@code process_no} 单列唯一键、
 * 以及逻辑删除后版本号被复用导致 {@code (process_no, version)} 撞键。</p>
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass"
})
@Testcontainers
class ProcessVersionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ProcessService processService;
    @Autowired
    private ProcessMapper processMapper;

    /**
     * 创建一个全新的草稿工艺。
     *
     * @return 新建工艺
     */
    private Process newDraft() {
        Process process = new Process();
        process.setProcessName("版本复制测试工艺");
        process.setProcessType("机加工");
        process.setOwner("tester");
        return processService.create(process);
    }

    @Test
    void sameProcessNoCanHoldMultipleVersions() {
        Process v1 = newDraft();

        Process v2 = processService.createVersion(v1.getId());
        Process v3 = processService.createVersion(v2.getId());

        assertEquals(v1.getProcessNo(), v2.getProcessNo());
        assertEquals(v1.getProcessNo(), v3.getProcessNo());
        assertEquals(Integer.valueOf(2), v2.getVersion());
        assertEquals(Integer.valueOf(3), v3.getVersion());
        assertEquals(3L, processMapper.selectCount(new LambdaQueryWrapper<Process>()
                .eq(Process::getProcessNo, v1.getProcessNo())));
    }

    @Test
    void nextVersionSkipsSoftDeletedHighestVersion() {
        Process v1 = newDraft();
        Process v2 = processService.createVersion(v1.getId());
        processMapper.deleteById(v2.getId());

        Process v3 = processService.createVersion(v1.getId());

        assertEquals(Integer.valueOf(3), v3.getVersion(), "逻辑删除的最高版本号不可复用");
    }
}
