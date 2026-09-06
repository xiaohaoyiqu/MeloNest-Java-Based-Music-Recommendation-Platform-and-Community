package com.haoran.music.common.dto;

import com.haoran.music.common.constant.CommonConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;





@Data
@NoArgsConstructor
@AllArgsConstructor

public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;





    private List<T> records;





    private Long total;





    private Long current;





    private Long size;





    private Long pages;




    public PageResult(List<T> records, Long total, Long current, Long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = (total + size - 1) / size;
    }











    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return new PageResult<>(
                records,
                total,
                page.longValue(),
                size.longValue()
        );
    }




    public static <T> PageResult<T> empty() {
        return new PageResult<>(new ArrayList<T>(), 0L, 1L, (long) CommonConstants.DEFAULT_SIZE);
    }
}
