package com.codegen.support;

import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenPipelineStep;
import com.codegen.vo.GenPipelineVO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 流水线 VO 装配：实体 → 前端 VO（Long 字符串化由注解完成）。
 */
@Component
public class PipelineAssembler {

    public GenPipelineVO toVO(GenPipeline head, List<GenPipelineStep> steps) {
        GenPipelineVO vo = new GenPipelineVO();
        vo.setId(head.getId());
        vo.setPipelineNo(head.getPipelineNo());
        vo.setProjectId(head.getProjectId());
        vo.setSystemName(head.getSystemName());
        vo.setSystemDesc(head.getSystemDesc());
        vo.setDbName(head.getDbName());
        vo.setBackendPort(head.getBackendPort());
        vo.setFrontendPort(head.getFrontendPort());
        vo.setMode(head.getMode());
        vo.setSeedMode(head.getSeedMode());
        vo.setStatus(head.getStatus());
        vo.setCurrentStep(head.getCurrentStep());
        vo.setFailReason(head.getFailReason());
        vo.setFrontendUrl(head.getFrontendUrl());
        vo.setBackendPid(head.getBackendPid());
        vo.setFrontendPid(head.getFrontendPid());
        vo.setCreateTime(head.getCreateTime());
        if (steps != null) {
            steps.stream()
                    .sorted(Comparator.comparing(GenPipelineStep::getStepOrder))
                    .forEach(s -> {
                        GenPipelineVO.Step vs = new GenPipelineVO.Step();
                        vs.setId(s.getId());
                        vs.setStepKey(s.getStepKey());
                        vs.setStepName(s.getStepName());
                        vs.setStepOrder(s.getStepOrder());
                        vs.setSide(s.getSide());
                        vs.setStatus(s.getStatus());
                        vs.setMessage(s.getMessage());
                        vs.setLogExcerpt(s.getLogExcerpt());
                        vs.setStartedAt(s.getStartedAt());
                        vs.setFinishedAt(s.getFinishedAt());
                        vo.getSteps().add(vs);
                    });
        }
        return vo;
    }
}
