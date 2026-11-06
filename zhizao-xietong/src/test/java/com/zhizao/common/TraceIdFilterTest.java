package com.zhizao.common;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求链路标识测试：复用入参、生成缺失值并清理 MDC。
 *
 * @author Caspian
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void reusesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[1];

        FilterChain chain = (req, res) -> observed[0] = MDC.get(TraceIdFilter.MDC_KEY);
        filter.doFilter(request, response, chain);

        assertEquals("trace-123", observed[0]);
        assertEquals("trace-123", response.getHeader(TraceIdFilter.HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }

    @Test
    void generatesTraceIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[1];

        FilterChain chain = (req, res) -> observed[0] = MDC.get(TraceIdFilter.MDC_KEY);
        filter.doFilter(request, response, chain);

        assertNotNull(observed[0]);
        assertTrue(observed[0].length() >= 16);
        assertEquals(observed[0], response.getHeader(TraceIdFilter.HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
