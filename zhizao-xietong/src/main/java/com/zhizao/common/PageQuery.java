package com.zhizao.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数收敛：统一默认值与条数上限，避免单个请求拉取全表。
 *
 * <p>页码小于 1 按第 1 页处理；每页条数小于 1 回落到默认值，超过上限按上限处理。
 * 上限只限制单次返回量，不改变既有分页语义。</p>
 *
 * @author Caspian
 */
public final class PageQuery {

    /** 默认每页条数，与接口约定一致。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限。 */
    public static final int MAX_PAGE_SIZE = 200;

    private PageQuery() {
    }

    /**
     * 构造已收敛的分页对象。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param <T>      记录类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize) {
        return new Page<>(Math.max(pageNum, 1), normalizeSize(pageSize));
    }

    /**
     * 把每页条数收敛到 [1, {@value #MAX_PAGE_SIZE}]，非正数回落默认值。
     *
     * @param pageSize 原始每页条数
     * @return 收敛后的每页条数
     */
    public static int normalizeSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
