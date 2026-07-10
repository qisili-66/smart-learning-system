package com.smartlearning.backend.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    /**
     * 当前页数据列表
     */
    private List<T> list;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页条数
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 从 MyBatis-Plus Page 对象快速转换
     */
    public static <T> PageVO<T> of(Page<T> page) {
        return PageVO.<T>builder()
                .list(page.getRecords())
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .pages((int) page.getPages())
                .build();
    }

    public static <T> PageVO<T> empty(Integer pageNum, Integer pageSize) {
        int current = PageUtils.normalizePageNum(pageNum);
        int size = PageUtils.normalizePageSize(pageSize);
        return PageVO.<T>builder()
                .list(Collections.emptyList())
                .total(0L)
                .pageNum(current)
                .pageSize(size)
                .pages(0)
                .build();
    }
}
