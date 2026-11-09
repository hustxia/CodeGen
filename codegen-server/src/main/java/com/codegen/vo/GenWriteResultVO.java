package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 写盘结果（V4.2 台账合一）：文件明细 + 台账失败计数。
 *
 * <p>台账写失败不再静默：失败条数放进返回体 {@code ledgerErrors}，
 * 文件已落盘、写盘本身不算失败，但「账没记上」必须让调用方看见——
 * 下次按台账回滚就会对不上这份文件。
 */
@Data
public class GenWriteResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 写盘批次号（三入口统一生成，回滚按这个号查台账）
     */
    private String batchNo;

    /**
     * 台账写失败条数（0 = 账目完整；> 0 = 有文件落盘但没记上账）
     */
    private Integer ledgerErrors = 0;

    private List<GenWriteVO> files = new ArrayList<>();

    /**
     * SQL 交付物落盘结果（V7.3 P0-2）：按系统一条，含 db/ 下文件明细与失败计数。
     */
    private List<GenSqlDeliverVO> sqlDeliveries = new ArrayList<>();
}
