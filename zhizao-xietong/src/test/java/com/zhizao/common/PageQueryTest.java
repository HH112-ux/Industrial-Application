package com.zhizao.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageQueryTest {

    @Test
    void keepsValidPageSize() {
        Page<Object> page = PageQuery.of(2, 50);

        assertEquals(2, page.getCurrent());
        assertEquals(50, page.getSize());
    }

    @Test
    void capsPageSizeAtUpperBound() {
        Page<Object> page = PageQuery.of(1, 100_000);

        assertEquals(PageQuery.MAX_PAGE_SIZE, page.getSize());
    }

    @Test
    void fallsBackToDefaultWhenPageSizeNotPositive() {
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, PageQuery.of(1, 0).getSize());
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, PageQuery.of(1, -5).getSize());
    }

    @Test
    void clampsPageNumberToOne() {
        assertEquals(1, PageQuery.of(0, 10).getCurrent());
        assertEquals(1, PageQuery.of(-3, 10).getCurrent());
    }
}
