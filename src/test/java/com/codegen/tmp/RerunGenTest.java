package com.codegen.tmp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.dto.GenApplyDTO;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenApplyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 临时：用修复后的自检重跑 wms 项目的 gen（等价平台「重新执行」），用完删除。
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mysql://localhost:3306/gen_platform?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
        "gen.output.backend-root=D:/workspace/codegen-demo-target",
        "gen.output.frontend-root=D:/workspace/codegen-demo-target",
        "gen.selfcheck.classpath=C:/Users/xiayuhong/Documents/trae_projects/codegen-demo-target/demo-lib/*",
        "gen.pipeline.maven=C:/dev/maven/bin/mvn.cmd",
        "gen.pipeline.java-home=D:/tool/jdk-21.0.12.1+1"
})
class RerunGenTest {

    private static final long PROJECT_ID = 2117812332970160129L;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenApplyService applyService;

    @Test
    void rerunGen() {
        List<GenTable> tables = genTableMapper.selectList(
                new LambdaQueryWrapper<GenTable>().eq(GenTable::getProjectId, PROJECT_ID));
        List<Long> ids = tables.stream().map(GenTable::getId).toList();
        System.out.println("TABLES=" + ids.size() + " ids=" + ids);

        GenApplyDTO dto = new GenApplyDTO();
        dto.setIds(ids);
        dto.setOverwrite(true);
        dto.setCompile(false);
        dto.setRegisterMenu(true);
        dto.setSyncRoute(true);
        dto.setSkipCheck(false);
        dto.setDryRun(false);

        var vo = applyService.apply(dto);
        System.out.println("APPLY passed=" + vo.getPassed() + " written=" + vo.getWritten());
        if (vo.getSelfCheck() != null) {
            System.out.println("SELFCHECK passed=" + vo.getSelfCheck().isPassed()
                    + " files=" + vo.getSelfCheck().getFileCount()
                    + " java=" + vo.getSelfCheck().getJavaCount()
                    + " test=" + vo.getSelfCheck().getTestCount()
                    + " errors=" + vo.getSelfCheck().getErrorCount()
                    + " warnings=" + vo.getSelfCheck().getWarningCount());
            vo.getSelfCheck().getIssues().forEach(i -> System.out.println("ISSUE " + i.getSeverity()
                    + " " + i.getFile() + ":" + i.getLine() + " " + i.getMessage()));
        }
        if (vo.getStages() != null) {
            vo.getStages().forEach(s -> System.out.println("STAGE " + s.getStage() + " " + s.getStatus()
                    + " " + s.getMessage()));
        }
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(vo.getPassed()));
    }
}
