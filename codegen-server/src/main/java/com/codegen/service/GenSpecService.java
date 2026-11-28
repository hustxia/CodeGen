package com.codegen.service;

import com.codegen.entity.GenSpecArtifact;

import java.util.List;

/**
 * SDD 规格工件链服务（V1.30 第一期）
 */
public interface GenSpecService {

    /**
     * 工件树：项目下五类工件各取最新版本
     *
     * @param projectId 项目ID
     * @return 工件列表（每类最多一条，最新版本）
     */
    List<GenSpecArtifact> tree(Long projectId);

    /**
     * 工件详情
     */
    GenSpecArtifact detail(Long id);

    /**
     * 指定项目指定类型的全部历史版本（version 倒序）
     */
    List<GenSpecArtifact> history(Long projectId, String artifactType);

    /**
     * 保存工件：一律新增版本快照（version = 当前最大 +1），不覆盖历史
     *
     * @return 新工件（含新版本号）
     */
    GenSpecArtifact save(GenSpecArtifact artifact);

    /**
     * 确认工件：DRAFT → CONFIRMED（converge 对账只认 CONFIRMED）
     */
    GenSpecArtifact confirm(Long id);

    /**
     * 初始化项目基本法（constitution）：已有则直接返回，否则按平台默认规范生成 DRAFT 版
     */
    GenSpecArtifact initConstitution(Long projectId);

    /**
     * 取项目最新 CONFIRMED 的 SPEC 工件；无 CONFIRMED 时回退最新 DRAFT
     *
     * @return 无则 null
     */
    GenSpecArtifact latestEffectiveSpec(Long projectId);
}
