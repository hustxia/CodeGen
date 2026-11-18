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
        return create(project, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysProject project, Long copyFromId) {
        validate(project, true);
        if (getByCode(project.getProjectCode()) != null) {
            throw new BusinessException(400, "系统编码已存在：" + project.getProjectCode());
        }
        if (copyFromId != null) {
            SysProject source = projectMapper.selectById(copyFromId);
            if (source == null) {
                throw new BusinessException(404, "被复制的系统不存在：" + copyFromId);
            }
            copyPathConfig(source, project);
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

    /**
     * 复制路径类配置与生成开关默认值。
     *
     * <p>只补目标对象里「没填」的字段：前端表单允许用户先手填再选复制源，
     * 手填的优先级高于复制源。编码、名称、根包名、网关前缀一律不碰。
     */
    private void copyPathConfig(SysProject source, SysProject target) {
        if (!StringUtils.hasText(target.getBackendRoot())) {
            target.setBackendRoot(source.getBackendRoot());
        }
        if (!StringUtils.hasText(target.getFrontendRoot())) {
            target.setFrontendRoot(source.getFrontendRoot());
        }
        if (!StringUtils.hasText(target.getBackendPath())) {
            target.setBackendPath(source.getBackendPath());
        }
        if (!StringUtils.hasText(target.getFrontendPath())) {
            target.setFrontendPath(source.getFrontendPath());
        }
        if (!StringUtils.hasText(target.getSelfcheckClasspath())) {
            target.setSelfcheckClasspath(source.getSelfcheckClasspath());
        }
        // V5.5 工程结构：复制系统连结构一起带（目标工程目录都复制了，结构不一致会出现
        // 多模块配置配着单模块路径的错位系统）
        if (!StringUtils.hasText(target.getProjectStructure())) {
            target.setProjectStructure(source.getProjectStructure());
        }
        // 生成开关默认值：整组复制，否则新系统还得再点 17 次
        if (target.getDefaultGenEntity() == null) {
            target.setDefaultGenEntity(source.getDefaultGenEntity());
        }
        if (target.getDefaultGenMapper() == null) {
            target.setDefaultGenMapper(source.getDefaultGenMapper());
        }
        if (target.getDefaultGenService() == null) {
            target.setDefaultGenService(source.getDefaultGenService());
        }
        if (target.getDefaultGenLog() == null) {
            target.setDefaultGenLog(source.getDefaultGenLog());
        }
        if (target.getDefaultGenController() == null) {
            target.setDefaultGenController(source.getDefaultGenController());
        }
        if (target.getDefaultGenPageApi() == null) {
            target.setDefaultGenPageApi(source.getDefaultGenPageApi());
        }
        if (target.getDefaultGenDetailApi() == null) {
            target.setDefaultGenDetailApi(source.getDefaultGenDetailApi());
        }
        if (target.getDefaultGenAddApi() == null) {
            target.setDefaultGenAddApi(source.getDefaultGenAddApi());
        }
        if (target.getDefaultGenEditApi() == null) {
            target.setDefaultGenEditApi(source.getDefaultGenEditApi());
        }
        if (target.getDefaultGenDeleteApi() == null) {
            target.setDefaultGenDeleteApi(source.getDefaultGenDeleteApi());
        }
        if (target.getDefaultGenFrontend() == null) {
            target.setDefaultGenFrontend(source.getDefaultGenFrontend());
        }
        if (target.getDefaultGenApiJs() == null) {
            target.setDefaultGenApiJs(source.getDefaultGenApiJs());
        }
        if (target.getDefaultGenVuePage() == null) {
            target.setDefaultGenVuePage(source.getDefaultGenVuePage());
        }
        if (target.getDefaultGenQueryForm() == null) {
            target.setDefaultGenQueryForm(source.getDefaultGenQueryForm());
        }
        if (target.getDefaultGenAddBtn() == null) {
            target.setDefaultGenAddBtn(source.getDefaultGenAddBtn());
        }
        if (target.getDefaultGenEditBtn() == null) {
            target.setDefaultGenEditBtn(source.getDefaultGenEditBtn());
        }
        if (target.getDefaultGenDeleteBtn() == null) {
            target.setDefaultGenDeleteBtn(source.getDefaultGenDeleteBtn());
        }
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
        // V5.5 收尾修复：projectStructure 是三态字段（null=单模块/跟随默认），
        // updateById 默认 NOT_NULL 策略不写 null，「multi 改回单模块」保存不上。
        // 显式再写一次该列：null 时生成 SET project_structure=NULL，非 null 重复写同值无害。
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getProjectStructure, project.getProjectStructure()));
        // V6.1 同款修复：flow_engine 三态（null=builtin 跟随默认），null 必须能存回
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getFlowEngine, project.getFlowEngine()));
        // V6.2 同款修复：default_gen_deploy 三态
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getDefaultGenDeploy, project.getDefaultGenDeploy()));
        // V6.3 同款修复：pay_enabled 三态
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getPayEnabled, project.getPayEnabled()));
        // V6.5 同款修复：mobile_enabled 三态
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getMobileEnabled, project.getMobileEnabled()));
        // V6.6 同款修复：file_enabled 三态
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getFileEnabled, project.getFileEnabled()));
        // V6.7 同款修复：test_enabled 三态（NULL=未配置开 / 1=生成 / 0=不生成）
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getTestEnabled, project.getTestEnabled()));
        // V6.8 同款修复：品牌三项都是「空=回落」语义，null 必须能存回去（清空品牌要生效）
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getAppName, project.getAppName())
                .set(SysProject::getAppLogo, project.getAppLogo())
                .set(SysProject::getAppThemeColor, project.getAppThemeColor()));
        // V8.0 同款修复：i18n_enabled 三态（NULL/0=关、1=开），null 必须能存回
        projectMapper.update(null, new LambdaUpdateWrapper<SysProject>()
                .eq(SysProject::getId, project.getId())
                .set(SysProject::getI18nEnabled, project.getI18nEnabled()));
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
