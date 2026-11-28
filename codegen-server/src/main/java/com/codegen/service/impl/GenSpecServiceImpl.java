package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.mapper.GenSpecArtifactMapper;
import com.codegen.service.GenSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SDD 规格工件链服务实现（V1.30 第一期）
 */
@Service
public class GenSpecServiceImpl implements GenSpecService {

    private static final List<String> ARTIFACT_TYPES = List.of(
            GenSpecArtifact.TYPE_CONSTITUTION,
            GenSpecArtifact.TYPE_SPEC,
            GenSpecArtifact.TYPE_PLAN,
            GenSpecArtifact.TYPE_TABLES,
            GenSpecArtifact.TYPE_TASKS);

    @Autowired
    private GenSpecArtifactMapper specArtifactMapper;

    @Override
    public List<GenSpecArtifact> tree(Long projectId) {
        List<GenSpecArtifact> all = listByProject(projectId);
        Map<String, GenSpecArtifact> latest = new LinkedHashMap<>();
        for (String type : ARTIFACT_TYPES) {
            latest.put(type, null);
        }
        for (GenSpecArtifact a : all) {
            GenSpecArtifact cur = latest.get(a.getArtifactType());
            if (cur == null || a.getVersion() > cur.getVersion()) {
                latest.put(a.getArtifactType(), a);
            }
        }
        List<GenSpecArtifact> result = new ArrayList<>();
        for (String type : ARTIFACT_TYPES) {
            GenSpecArtifact a = latest.get(type);
            if (a != null) {
                result.add(a);
            }
        }
        return result;
    }

    @Override
    public GenSpecArtifact detail(Long id) {
        return specArtifactMapper.selectById(id);
    }

    @Override
    public List<GenSpecArtifact> history(Long projectId, String artifactType) {
        List<GenSpecArtifact> all = listByProject(projectId);
        return all.stream()
                .filter(a -> Objects.equals(a.getArtifactType(), artifactType))
                .sorted(Comparator.comparing(GenSpecArtifact::getVersion).reversed())
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenSpecArtifact save(GenSpecArtifact artifact) {
        if (artifact.getProjectId() == null || artifact.getArtifactType() == null) {
            throw new IllegalArgumentException("projectId 与 artifactType 不能为空");
        }
        if (!ARTIFACT_TYPES.contains(artifact.getArtifactType())) {
            throw new IllegalArgumentException("非法工件类型: " + artifact.getArtifactType());
        }
        int nextVersion = listByProject(artifact.getProjectId()).stream()
                .filter(a -> Objects.equals(a.getArtifactType(), artifact.getArtifactType()))
                .mapToInt(GenSpecArtifact::getVersion)
                .max()
                .orElse(0) + 1;
        artifact.setId(null);
        artifact.setVersion(nextVersion);
        artifact.setStatus(GenSpecArtifact.STATUS_DRAFT);
        specArtifactMapper.insert(artifact);
        return artifact;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenSpecArtifact confirm(Long id) {
        GenSpecArtifact a = specArtifactMapper.selectById(id);
        if (a == null) {
            throw new IllegalArgumentException("工件不存在: " + id);
        }
        a.setStatus(GenSpecArtifact.STATUS_CONFIRMED);
        specArtifactMapper.updateById(a);
        return a;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenSpecArtifact initConstitution(Long projectId) {
        GenSpecArtifact existing = listByProject(projectId).stream()
                .filter(a -> GenSpecArtifact.TYPE_CONSTITUTION.equals(a.getArtifactType()))
                .max(Comparator.comparing(GenSpecArtifact::getVersion))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        GenSpecArtifact c = new GenSpecArtifact();
        c.setProjectId(projectId);
        c.setArtifactType(GenSpecArtifact.TYPE_CONSTITUTION);
        c.setTitle("项目基本法（平台默认）");
        c.setYamlMeta("{\"domain\":\"\",\"naming\":\"snake_case\",\"enumStorage\":\"tinyint\",\"frontend\":\"vue3\"}");
        c.setContentMd(defaultConstitutionMd());
        c.setStructuredJson(null);
        return save(c);
    }

    @Override
    public GenSpecArtifact latestEffectiveSpec(Long projectId) {
        List<GenSpecArtifact> all = listByProject(projectId).stream()
                .filter(a -> GenSpecArtifact.TYPE_SPEC.equals(a.getArtifactType()))
                .toList();
        return all.stream()
                .filter(a -> GenSpecArtifact.STATUS_CONFIRMED.equals(a.getStatus()))
                .max(Comparator.comparing(GenSpecArtifact::getVersion))
                .orElseGet(() -> all.stream()
                        .max(Comparator.comparing(GenSpecArtifact::getVersion))
                        .orElse(null));
    }

    private List<GenSpecArtifact> listByProject(Long projectId) {
        return specArtifactMapper.selectList(new LambdaQueryWrapper<GenSpecArtifact>()
                .eq(GenSpecArtifact::getProjectId, projectId)
                .orderByDesc(GenSpecArtifact::getVersion));
    }

    /**
     * 平台默认基本法：沉淀自坑库的硬约定，项目可改可增
     */
    private String defaultConstitutionMd() {
        return """
                # 项目基本法（constitution）

                > 一次设定，全程遵守。本文件由人与 Agent 共读，修改后须重新 CONFIRM。

                ## 一、命名
                - 表名：业务前缀 + 小写下划线（如 cm_event）
                - 字段：小写下划线，禁用数据库保留字
                - 实体类：大驼峰；Java 属性：小驼峰

                ## 二、枚举
                - 状态/类型字段一律 tinyint 存储
                - 字典编码命名：{业务前缀}_{表}_{字段}（如 cg_event_status）
                - 枚举值只增不改，保留历史数据语义

                ## 三、ID 与精度
                - 主键雪花 ID；下发前端一律字符串序列化（2^53 精度红线）

                ## 四、前端
                - 枚举列绑定字典渲染中文标签；外键列渲染远程搜索下拉
                - 列表默认 20 条/页；时间字段 datetime

                ## 五、红线
                - 用户/角色/菜单/字典属基座 15 表，不得作为业务系统单独生成
                - GPS 轨迹等高频时序数据不进业务库
                """;
    }
}
