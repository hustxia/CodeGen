package com.codegen.service;

import com.codegen.dto.GenDiffDTO;
import com.codegen.dto.GenDownloadDTO;
import com.codegen.dto.GenWriteDTO;
import com.codegen.vo.GenDiffVO;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenSqlDeliverVO;
import com.codegen.vo.GenWriteVO;

import java.util.List;

/**
 * 代码生成 ZIP 服务
 */
public interface GenCodeService {

    /**
     * 按表配置 ID 列表渲染代码并打包 ZIP
     *
     * @param dto 表配置 ID 列表
     * @return ZIP 字节与文件清单
     * @throws com.codegen.common.exception.BusinessException 未勾选表 / 路径非法
     */
    GenZipResult generateZip(GenDownloadDTO dto);

    /**
     * 生成预览：渲染文件清单（路径+内容），不落盘
     */
    List<GenFileVO> preview(GenDownloadDTO dto);

    /**
     * 写盘：按每表 backend_root / frontend_root 写入磁盘
     *
     * @param dto 表配置 ID 列表 + 覆盖开关
     * @return 每文件写盘结果（WRITTEN/SKIPPED/ERROR）
     */
    List<GenWriteVO> writeToDisk(GenWriteDTO dto);

    /**
     * 写盘（带备份）：覆盖已有文件前先复制到 {@code <root>/.gen-backup/<batchNo>/}，
     * 并把每个文件的结果（含备份路径、覆盖前 md5）写入 gen_output 台账，供误覆盖后回滚与漂移检测。
     *
     * @param dto     表配置 ID 列表 + 覆盖开关（覆盖默认开启）
     * @param batchNo 批次号；为空则等同 {@link #writeToDisk(GenWriteDTO)}
     */
    List<GenWriteVO> writeToDisk(GenWriteDTO dto, String batchNo);

    /**
     * 本次写盘中台账写入失败的条数（V4.2）。
     *
     * <p>台账失败不阻断写盘，但不能静默：调用方应把它放进响应（如 {@code X-Gen-Ledger-Errors}），
     * 让「文件写了但台账没记上」这件事当场可见，而不是等下次回滚时才发现对不上。
     *
     * @return 失败条数，正常为 0
     */
    int ledgerErrorCount();

    /**
     * 本次写盘时 SQL 交付物（db/init_tables.sql 等）的落盘结果（V7.3 P0-2）。
     *
     * <p>按系统分组，一个系统一条；交付失败不阻断写盘，但要让调用方看见。
     *
     * @return 正常为空列表
     */
    List<GenSqlDeliverVO> lastSqlDeliveries();

    /**
     * 差异预览：渲染结果 vs 磁盘现状，逐文件行级 diff（不落盘）
     */
    List<GenDiffVO> diff(GenDiffDTO dto);

    /**
     * ZIP 生成结果：字节流 + 文件相对路径清单
     */
    record GenZipResult(byte[] bytes, List<String> files) {
    }
}
