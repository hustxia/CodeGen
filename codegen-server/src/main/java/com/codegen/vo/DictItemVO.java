package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项（前端下拉/单选/回显用）
 */
@Data
public class DictItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 展示文本
     */
    private String label;

    /**
     * 存储值
     */
    private String value;

    public DictItemVO() {
    }

    public DictItemVO(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
