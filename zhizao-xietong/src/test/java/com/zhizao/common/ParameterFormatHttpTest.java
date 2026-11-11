package com.zhizao.common;

import com.zhizao.controller.ScheduleController;
import com.zhizao.service.ScheduleService;
import com.zhizao.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证时间等查询参数格式错误返回 HTTP 400 与可读原因，而不是落到兜底 500。
 *
 * @author Caspian
 */
@WebMvcTest(controllers = ScheduleController.class)
@ContextConfiguration(classes = ParameterFormatHttpTest.EmptyConfig.class)
@Import({ScheduleController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class ParameterFormatHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleService scheduleService;

    @MockBean
    private WorkOrderService workOrderService;

    @Test
    void malformedTimeParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/schedules/devices")
                        .param("from", "2026-11-01T00:00:00")
                        .param("to", "2026-11-20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数 from 格式不正确，日期格式应为 yyyy-MM-dd"));

        verifyNoInteractions(scheduleService);
    }

    @Test
    void isoDateParameterStillReachesService() throws Exception {
        when(scheduleService.deviceTimeline(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/schedules/devices")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scheduleService).deviceTimeline(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20));
    }
}
