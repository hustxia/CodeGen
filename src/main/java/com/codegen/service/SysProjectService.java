package com.codegen.service;

import com.codegen.entity.SysProject;

import java.util.List;

/**
 * 系统台账服务。
 */
public interface SysProjectService {

    /**
     * 全部系统（含停用），按 sort 升序。停用的也返回，方便管理页展示与编辑。
     */
    List<SysProject> listAll();

    /**
     * 仅启用的系统，给「新建表」的下拉用。
     */
    List<SysProject> listEnabled();

    /**
     * 默认系统；一条都没有时返回 null。
     */
    SysProject getDefault();

    /**
     * 按 ID 取，不存在返回 null。
     */
    SysProject getById(Long id);

    /**
     * 按编码取，不存在返回 null。
     */
    SysProject getByCode(String projectCode);

    /**
     * 新建。编码重复会抛出 BusinessException。
     */
    Long create(SysProject project);

    /**
     * 新建，并从已有系统复制配置。
     *
     * <p>只复制「路径类 + 生成开关默认值」，<b>不复制</b>编码、名称、根包名、网关前缀：
     * 路径是「这台机器上的目录约定」，兄弟系统通常一样；
     * 而根包名和网关前缀属于系统身份，抄过来会得到两个 com.demo 的系统，
     * 生成的代码互相覆盖，比让用户多填两个字段危险得多。
     *
     * @param copyFromId 被复制的系统 ID，null 等同不复制
     */
    Long create(SysProject project, Long copyFromId);

    /**
     * 修改。编码一旦有表挂上去就不允许改，否则已生成的产物与记录会对不上。
     */
    void update(SysProject project);

    /**
     * 删除。系统下还有表时拒绝删除，避免表变成孤儿。
     */
    void delete(Long id);

    /**
     * 设为默认系统。会先把其他系统的 is_default 清掉，
     * 否则出现两个默认，新建表时无法确定预选哪个。
     */
    void setDefault(Long id);
}
