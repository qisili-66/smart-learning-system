package com.smartlearning.backend.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public final class PageUtils {

    private PageUtils() {
    }

    public static int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return Constants.DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return Constants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, Constants.MAX_PAGE_SIZE);
    }

    public static <T> Page<T> page(Integer pageNum, Integer pageSize) {
        return new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
    }
}
