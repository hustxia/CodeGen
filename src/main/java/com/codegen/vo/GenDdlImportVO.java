package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DDL 文本一键导入的逐表校验报告。
 *
 * <p>status 取值：
 * <ul>
 *   <li>SAVED —— 解析并导入成功；</li>
 *   <li>EXISTS —— 表已注册过生成配置，跳过；</li>
 *   <li>FAILED —— 解析或落库失败，见 message。</li>
 * </ul>
 *
 * <p>「字典信息正确导入」由两层校验保障：
 * 回读落库列数与 DDL 解析列数比对；字典候选列回读 dict_type 是否已回填。
 */
@Data
public class GenDdlImportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;

    private String status;

    private String message;

    /**
     * DDL 里声明的表注释（未声明时与表名相同）
     */
    private String tableComment;

    /**
     * 落库后的列数
     */
    private Integer columnCount;

    /**
     * 缺列注释的字段名
     */
    private List<String> missingCommentColumns = new ArrayList<>();

    /**
     * 自动识别并落库的字典数
     */
    private Integer dictCount;

    /**
     * 疑似枚举（enum 类型或注释带枚举说明）但 dict_type 未回填的列——字典漏建预警
     */
    private List<String> dictMissingColumns = new ArrayList<>();

    /**
     * 其他校验警告（列数不一致、缺表注释等）
     */
    private List<String> warnings = new ArrayList<>();
}
