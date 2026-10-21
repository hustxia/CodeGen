package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * P2-3 字典落库入参。
 *
 * <p>{@code dictCodes} 为空表示把该表全部识别结果都落库。
 */
@Data
public class GenDictApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * gen_table.id
     */
    private Long tableId;

    /**
     * 要落库的字典编码子集；null/空 = 全部
     */
    private List<String> dictCodes;
}
