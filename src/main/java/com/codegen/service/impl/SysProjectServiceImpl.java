package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.SysProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 系统台账实现。
 */
@Service
public class SysProjectServiceImpl implements SysProjectService {

    /**
     * 编码要拼进包名、字典编码、权限点，只允许小写字母数字和下划线。
     * 大写放进来会引出大小写不一致的问题（Windows 生成、Linux 跑）。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    @Autowired
    private SysProjectMapper projectMapper;

    @Autowired
    private GenTableMapper genTableMapper;

    @Override
    public List<SysProject> listAll() {
        LambdaQueryWrapper<SysProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysProject::getIsDefault).orderByAsc(SysProject::getSort).orderByAsc(SysProject::getId);
        return projectMapper.selectList(wrapper);
    }

    @Override
    public List<SysProject> listEnabled() {
        LambdaQueryWrapper<SysProject> wrapper = new LambdaQueryWrapper<>();
        // 默认系统排最前，前端下拉一打开就是它
        wrapper.eq(SysProject::getEnabled, 1)
                .orderByDesc(SysProject::getIsDefault)
                .orderByAsc(SysProject::getSort)
                .orderByAsc(SysProject::getId);
        return projectMapper.selectList(wrapper);
    }

    @Override
    public SysProject getDefault() {
        LambdaQueryWrapper<SysProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysProject::getIsDefault, 1).orderByAsc(SysProject::getId).last("LIMIT 1");
        return projectMapper.selectOne(wrapper);
    }

    @Override
    public SysProject getById(Long id) {
        return id == null ? null : projectMapper.selectById(id);
    }

    @Override
    public SysProject getByCode(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return null;
        }
        LambdaQueryWrapper<SysProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysProject::getProjectCode, projectCode.trim()).last("LIMIT 1");
        return projectMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysProject project) {
        validate(project, true);
        if (getByCode(project.getProjectCode()) != null) {
            throw new BusinessException(400, "系统编码已存在：" + project.getProjectCode());
        }
        if (project.getEnabled() == null) {
            project.setEnabled(1);
        }
        if (project.getSort() == null) {
            project.setSort(0);
        }
        // 第一条系统自动成为默认，省掉用户「建完还得再点一次设为默认」
        if (getDefault() == null) {
            project.setIsDefault(1);
        } else {
            project.setIsDefault(0);
        }
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysProject project) {
        if (project.getId() == null) {
            throw new BusinessException(400, "缺少系统 ID");
        }
        SysProject old = projectMapper.selectById(project.getId());
        if (old == null) {
            throw new BusinessException(404, "系统不存在：" + project.getId());
        }
        validate(project, false);

        // 编码是生成物的路径锚点，改了会让已生成的文件与台账全部错位
        if (StringUtils.hasText(project.getProjectCode())
                && !project.getProjectCode().equals(old.getProjectCode())) {
            if (countTables(project.getId()) > 0) {
                throw new BusinessException(400, "该系统下已有表，系统编码不允许修改");
            }
        }
        // 默认标记只走 setDefault，避免这里被改成 0 导致一个默认都没有
        project.setIsDefault(old.getIsDefault());
        projectMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysProject old = projectMapper.selectById(id);
        if (old == null) {
            throw new BusinessException(404, "系统不存在：" + id);
        }
        long tables = countTables(id);
        if (tables > 0) {
            // 直接删会让这些表变成孤儿：project_id 指向一个不存在的系统，生成时回落规则失效
            throw new BusinessException(400, "该系统下还有 " + tables + " 张表，请先转移或删除后再操作");
        }
        if (Integer.valueOf(1).equals(old.getIsDefault())) {
            throw new BusinessException(400, "默认系统不能删除，请先把其他系统设为默认");
        }
        projectMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        SysProject target = projectMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(404, "系统不存在：" + id);
        }
        if (!Integer.valueOf(1).equals(target.getEnabled())) {
            throw new BusinessException(400, "停用的系统不能设为默认");
        }
        // 先全清再置位：两条 UPDATE 在同一个事务里，不会出现中途失败留下两个默认
        LambdaUpdateWrapper<SysProject> clear = new LambdaUpdateWrapper<>();
        clear.set(SysProject::getIsDefault, 0).ne(SysProject::getId, id);
        projectMapper.update(null, clear);

        SysProject patch = new SysProject();
        patch.setId(id);
        patch.setIsDefault(1);
        projectMapper.updateById(patch);
    }

    private long countTables(Long projectId) {
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTable::getProjectId, projectId);
        return genTableMapper.selectCount(wrapper);
    }

    private void validate(SysProject project, boolean requireCode) {
        if (project == null) {
            throw new BusinessException(400, "系统信息不能为空");
        }
        if (requireCode || StringUtils.hasText(project.getProjectCode())) {
            String code = project.getProjectCode() == null ? "" : project.getProjectCode().trim();
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw new BusinessException(400, "系统编码只能是小写字母、数字、下划线，且以字母开头");
            }
            project.setProjectCode(code);
        }
        if (requireCode && !StringUtils.hasText(project.getProjectName())) {
            throw new BusinessException(400, "系统名称不能为空");
        }
        // 包名只做轻校验：真正的合法性在生成时由编译暴露，这里挡住明显的拼写错误
        if (StringUtils.hasText(project.getRootPackage())
                && !project.getRootPackage().matches("^[a-z][a-z0-9_.]*$")) {
            throw new BusinessException(400, "根包名格式不正确，应为 com.example 这样的形式");
        }
    }
}
