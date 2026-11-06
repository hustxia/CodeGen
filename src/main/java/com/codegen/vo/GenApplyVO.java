package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一键生成结果：分阶段回传，前端按阶段展示「哪一步过了、哪一步挂了」。
 */
@Data
public class GenApplyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次号：写盘历史与回滚都按这个号查
     */
    private String batchNo;

    private Boolean passed;

    private Long elapsedMs;

    private List<GenApplyStageVO> stages = new ArrayList<>();

    private Integer written;

    private Integer skipped;

    private Integer failed;

    /**
     * 本批次备份根目录（有文件被覆盖时才有值）
     */
    private String backupDir;

    /**
     * 台账写失败条数（V4.2 台账合一）：> 0 表示有文件落盘但 gen_output 没记上账，
     * 按台账回滚时这些文件会缺失，需要人工核对
     */
    private Integer ledgerErrors = 0;

    /**
     * 自检明细（stage=CHECK 时有值）
     */
    private GenSelfCheckVO selfCheck;

    /**
     * 菜单注册结果（stage=MENU 时有值）
     */
    private GenMenuRegisterVO menu;

    /**
     * 写盘明细，前端可展开看每个文件
     */
    private List<GenWriteVO> files = new ArrayList<>();

    /**
     * V6.8 重生成影响面：dryRun 时回填（新增/修改/未变/受保护 + 逐文件明细），
     * 实际写盘时也回填（此时 protected 项与 files 里的 SKIPPED 对应）。
     */
    private GenImpactVO impact;

    public GenApplyVO stage(GenApplyStageVO stage) {
        this.stages.add(stage);
        return this;
    }
}
