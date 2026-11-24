package ${pkg}.service.impl;

<#if genTree || hasChild>import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
</#if>
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${pkg}.entity.${className};
import ${pkg}.mapper.${className}Mapper;
import ${pkg}.service.${className}Service;
<#if genTree>import ${pkg}.vo.${className}VO;
</#if>
import ${pkg}.dto.${className}SaveDTO;
<#list children as child>
import ${pkg}.dto.${child.className}SaveDTO;
import ${pkg}.entity.${child.className};
import ${pkg}.mapper.${child.className}Mapper;
<#-- 孙层的实体与 Mapper 同样要导入：save${gc.className}Rows 与级联删除都直接引用这两个类型。
     漏了 import，生成的主子表实现类 javac 会直接「找不到符号」 -->
<#list child.grandChildren as gc>
import ${gc.pkg}.entity.${gc.className};
import ${gc.pkg}.mapper.${gc.className}Mapper;
</#list>
</#list>
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
<#if deleteCascadeBlock>import ${rootPkg}.common.exception.BusinessException;
</#if>
<#if genTree || childIncremental>import java.util.ArrayList;
</#if>
import java.util.List;
<#if genTree>import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
</#if>

/**
 * ${comment} Service
 */
@Service
public class ${className}ServiceImpl extends ServiceImpl<${className}Mapper, ${className}> implements ${className}Service {

    // <gen:begin key="service-body">
<#if hasChild>
<#list children as child>
    @Autowired
    private ${child.className}Mapper ${child.varName}Mapper;
<#-- 孙表 Mapper 必须跟着声明：saveXxxRows 的全量替换要用孙表 Mapper，漏了声明生成的代码编译不过 -->
<#list child.grandChildren as gc>
    @Autowired
    private ${gc.className}Mapper ${gc.varName}Mapper;
</#list>
</#list>
</#if>
<#if genTree>
    @Override
    public List<${className}VO> tree() {
        List<${className}> all = list(new LambdaQueryWrapper<${className}>().orderByAsc(${className}::getId));
        List<${className}VO> vos = new ArrayList<>();
        for (${className} entity : all) {
            ${className}VO vo = new ${className}VO();
            BeanUtils.copyProperties(entity, vo);
            vos.add(vo);
        }
        Map<Long, ${className}VO> index = vos.stream()
                .collect(Collectors.toMap(${className}VO::getId, Function.identity(), (a, b) -> a));
        List<${className}VO> roots = new ArrayList<>();
        for (${className}VO vo : vos) {
            ${treeParentJavaType} parentId = vo.get${treeParentCapField}();
            Long parentKey = parentId == null ? null : Long.valueOf(String.valueOf(parentId));
            ${className}VO parent = parentKey == null ? null : index.get(parentKey);
            if (parent == null || parent == vo) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }
</#if>
<#if hasChild>
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithChildren(${className}SaveDTO master<#list children as child>,
                                 List<${child.className}SaveDTO> ${child.varName}Rows</#list>) {
        ${className} entity = new ${className}();
        BeanUtils.copyProperties(master, entity);
        saveOrUpdate(entity);
<#list children as child>
        replace${child.className}Rows(entity.getId(), ${child.varName}Rows);
</#list>
    }
<#list children as child>
<#if childIncremental>
<#if child.oneToOne>
    /**
     * ${child.comment} 写回（child_save_mode=1，一对一）：外键唯一，已存在则更新、不存在则插入。
     * rows 为 null 表示本次提交未涉及该子表（不动），空列表表示解除这条一对一关联。
     */
    private void replace${child.className}Rows(Long masterId, List<${child.className}SaveDTO> rows) {
        if (masterId == null || rows == null) {
            return;
        }
        ${child.className}Mapper childMapper = ${child.varName}Mapper;
        if (rows.isEmpty()) {
            childMapper.delete(new LambdaQueryWrapper<${child.className}>()
                    .eq(${child.className}::get${child.fkCapField}, masterId));
            return;
        }
        // 一对一：只认首行；身份是外键而不是回传主键，改主键的脏提交也不会插出第二行
        ${child.className}SaveDTO dto = rows.get(0);
        List<${child.className}> existing = childMapper.selectList(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, masterId));
        if (existing.isEmpty()) {
            ${child.className} child = new ${child.className}();
            BeanUtils.copyProperties(dto, child);
            // 新行：主键重新生成，审计字段交给自动填充
            child.setId(null);
            child.setCreateTime(null);
            child.setUpdateTime(null);
            child.setCreateBy(null);
            child.setUpdateBy(null);
            child.set${child.fkCapField}(masterId);
            childMapper.insert(child);
<#list child.grandChildren as gc>
            save${gc.className}Rows(child.getId(), dto.get${gc.className}s());
</#list>
        } else {
            ${child.className} child = existing.get(0);
            Long existingId = child.getId();
            BeanUtils.copyProperties(dto, child);
            // 保留既有主键与审计字段，只覆盖业务列（updateById 会跳过 null，避免把列值抹掉）
            child.setId(existingId);
            child.setUpdateTime(null);
            child.setUpdateBy(null);
            child.set${child.fkCapField}(masterId);
            childMapper.updateById(child);
<#list child.grandChildren as gc>
            save${gc.className}Rows(existingId, dto.get${gc.className}s());
</#list>
        }
    }
<#elseif child.manyToMany && child.hasRelationKey && !child.hasGrandChild>
    /**
     * ${child.comment} 写回（child_save_mode=1，多对多中间表）：按关联键做差集，只删该删、只插新关联。
     * 同一关联对象在本次提交里重复出现只会落一行，重复保存不会产生重复关联行。
     * rows 为 null 表示本次提交未涉及该子表（不动），空列表表示清空全部关联。
     */
    private void replace${child.className}Rows(Long masterId, List<${child.className}SaveDTO> rows) {
        if (masterId == null || rows == null) {
            return;
        }
        ${child.className}Mapper childMapper = ${child.varName}Mapper;
        List<${child.className}> existing = childMapper.selectList(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, masterId));
        // 本次提交的关联键（去重）
        List<Object> incomingKeys = new ArrayList<>();
        for (${child.className}SaveDTO dto : rows) {
            Object key = dto.get${child.relationKeyCapField}();
            if (key != null && !incomingKeys.contains(key)) {
                incomingKeys.add(key);
            }
        }
        // 差集删除：库里有、本次没提交的关联行
        List<Long> removeIds = new ArrayList<>();
        for (${child.className} old : existing) {
            if (old.get${child.relationKeyCapField}() == null
                    || !incomingKeys.contains(old.get${child.relationKeyCapField}())) {
                removeIds.add(old.getId());
            }
        }
        if (!removeIds.isEmpty()) {
            childMapper.deleteBatchIds(removeIds);
        }
        // 差集新增：已经存在的关联不再插，保证幂等
        List<Object> existingKeys = new ArrayList<>();
        for (${child.className} old : existing) {
            if (old.get${child.relationKeyCapField}() != null) {
                existingKeys.add(old.get${child.relationKeyCapField}());
            }
        }
        for (${child.className}SaveDTO dto : rows) {
            Object key = dto.get${child.relationKeyCapField}();
            if (key == null || existingKeys.contains(key)) {
                continue;
            }
            ${child.className} child = new ${child.className}();
            BeanUtils.copyProperties(dto, child);
            child.setId(null);
            child.setCreateTime(null);
            child.setUpdateTime(null);
            child.setCreateBy(null);
            child.setUpdateBy(null);
            child.set${child.fkCapField}(masterId);
            childMapper.insert(child);
            existingKeys.add(key);
        }
    }
<#else>
    /**
     * ${child.comment} 增量保存（child_save_mode=1）：按主键三路合并，与主表同一事务。
     * 新增 insert、变更 update、本次未提交的旧行 delete；不再先 delete-all。
     * rows 为 null 表示本次提交未涉及该子表（不动），空列表表示清空。
     */
    private void replace${child.className}Rows(Long masterId, List<${child.className}SaveDTO> rows) {
        if (masterId == null || rows == null) {
            return;
        }
        ${child.className}Mapper childMapper = ${child.varName}Mapper;
        List<${child.className}> existing = childMapper.selectList(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, masterId));
        List<Long> keepIds = new ArrayList<>();
        for (${child.className}SaveDTO dto : rows) {
            ${child.className} matched = null;
            for (${child.className} old : existing) {
                if (dto.getId() != null && dto.getId().equals(old.getId())) {
                    matched = old;
                    break;
                }
            }
            if (matched == null) {
                // 三路合并·新增：主键为空或库里没有 → 重新生成主键与审计字段
                ${child.className} child = new ${child.className}();
                BeanUtils.copyProperties(dto, child);
                child.setId(null);
                child.setCreateTime(null);
                child.setUpdateTime(null);
                child.setCreateBy(null);
                child.setUpdateBy(null);
                child.set${child.fkCapField}(masterId);
                childMapper.insert(child);
                keepIds.add(child.getId());
<#list child.grandChildren as gc>
                save${gc.className}Rows(child.getId(), dto.get${gc.className}s());
</#list>
            } else {
                // 三路合并·更新：沿用原主键，审计字段不重置（update_by/update_time 交给自动填充）
                Long existingId = matched.getId();
                BeanUtils.copyProperties(dto, matched);
                matched.setId(existingId);
                matched.setUpdateTime(null);
                matched.setUpdateBy(null);
                matched.set${child.fkCapField}(masterId);
                childMapper.updateById(matched);
                keepIds.add(existingId);
<#list child.grandChildren as gc>
                save${gc.className}Rows(existingId, dto.get${gc.className}s());
</#list>
            }
        }
        // 三路合并·删除：库里有、本次没提交的行，按主键集合一次删（逻辑删除）
        List<Long> removeIds = new ArrayList<>();
        for (${child.className} old : existing) {
            if (old.getId() != null && !keepIds.contains(old.getId())) {
                removeIds.add(old.getId());
            }
        }
        if (!removeIds.isEmpty()) {
            childMapper.deleteBatchIds(removeIds);
        }
    }
</#if>
<#list child.grandChildren as gc>
<#if child.oneToOne || child.manyToMany>
    /**
     * ${gc.comment} 全量替换。抽成独立方法，避免保存主流程出现三层嵌套循环。
     */
    private void save${gc.className}Rows(Long parentId, List<${gc.className}SaveDTO> rows) {
        if (parentId == null) {
            return;
        }
        ${gc.className}Mapper mapper = ${gc.varName}Mapper;
        mapper.delete(new LambdaQueryWrapper<${gc.className}>()
                .eq(${gc.className}::get${gc.fkCapField}, parentId));
        if (rows == null) {
            return;
        }
        for (${gc.className}SaveDTO dto : rows) {
            ${gc.className} row = new ${gc.className}();
            BeanUtils.copyProperties(dto, row);
            row.setId(null);
            row.setCreateTime(null);
            row.setUpdateTime(null);
            row.setCreateBy(null);
            row.setUpdateBy(null);
            row.set${gc.fkCapField}(parentId);
            mapper.insert(row);
        }
    }
<#else>
    /**
     * ${gc.comment} 增量保存：按主键三路合并，避免三层明细整体重建。
     */
    private void save${gc.className}Rows(Long parentId, List<${gc.className}SaveDTO> rows) {
        if (parentId == null || rows == null) {
            return;
        }
        ${gc.className}Mapper mapper = ${gc.varName}Mapper;
        List<${gc.className}> existing = mapper.selectList(new LambdaQueryWrapper<${gc.className}>()
                .eq(${gc.className}::get${gc.fkCapField}, parentId));
        List<Long> keepIds = new ArrayList<>();
        for (${gc.className}SaveDTO dto : rows) {
            ${gc.className} matched = null;
            for (${gc.className} old : existing) {
                if (dto.getId() != null && dto.getId().equals(old.getId())) {
                    matched = old;
                    break;
                }
            }
            if (matched == null) {
                ${gc.className} row = new ${gc.className}();
                BeanUtils.copyProperties(dto, row);
                row.setId(null);
                row.setCreateTime(null);
                row.setUpdateTime(null);
                row.setCreateBy(null);
                row.setUpdateBy(null);
                row.set${gc.fkCapField}(parentId);
                mapper.insert(row);
                keepIds.add(row.getId());
            } else {
                Long existingId = matched.getId();
                BeanUtils.copyProperties(dto, matched);
                matched.setId(existingId);
                matched.setUpdateTime(null);
                matched.setUpdateBy(null);
                matched.set${gc.fkCapField}(parentId);
                mapper.updateById(matched);
                keepIds.add(existingId);
            }
        }
        List<Long> removeIds = new ArrayList<>();
        for (${gc.className} old : existing) {
            if (old.getId() != null && !keepIds.contains(old.getId())) {
                removeIds.add(old.getId());
            }
        }
        if (!removeIds.isEmpty()) {
            mapper.deleteBatchIds(removeIds);
        }
    }
</#if>
</#list>
<#else>
    /**
     * ${child.comment} 全量替换：先按外键逻辑删除旧行，再逐行插入，与主表同一事务。
     * rows 为 null 表示本次提交未涉及该子表（不动），空列表表示清空。
     */
    private void replace${child.className}Rows(Long masterId, List<${child.className}SaveDTO> rows) {
        if (masterId == null) {
            return;
        }
        if (rows == null) {
            return;
        }
        ${child.className}Mapper childMapper = ${child.varName}Mapper;
        childMapper.delete(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, masterId));
        for (${child.className}SaveDTO dto : rows) {
            ${child.className} child = new ${child.className}();
            BeanUtils.copyProperties(dto, child);
            // 关键：旧行是逻辑删除（deleted=1，物理行仍在），沿用回传主键会撞 PRIMARY。
            // 一律清空主键与审计字段，让雪花 ID 重新生成、时间交给自动填充。
            child.setId(null);
            child.setCreateTime(null);
            child.setUpdateTime(null);
            child.setCreateBy(null);
            child.setUpdateBy(null);
            child.set${child.fkCapField}(masterId);
            childMapper.insert(child);
<#list child.grandChildren as gc>
            // 三级：这一行的下级也要跟着落库，外键指向刚插入的这行
            save${gc.className}Rows(child.getId(), dto.get${gc.className}s());
</#list>
        }
    }
<#list child.grandChildren as gc>
    /**
     * ${gc.comment} 全量替换。抽成独立方法，避免保存主流程出现三层嵌套循环。
     */
    private void save${gc.className}Rows(Long parentId, List<${gc.className}SaveDTO> rows) {
        if (parentId == null) {
            return;
        }
        ${gc.className}Mapper mapper = ${gc.varName}Mapper;
        mapper.delete(new LambdaQueryWrapper<${gc.className}>()
                .eq(${gc.className}::get${gc.fkCapField}, parentId));
        if (rows == null) {
            return;
        }
        for (${gc.className}SaveDTO dto : rows) {
            ${gc.className} row = new ${gc.className}();
            BeanUtils.copyProperties(dto, row);
            row.setId(null);
            row.setCreateTime(null);
            row.setUpdateTime(null);
            row.setCreateBy(null);
            row.setUpdateBy(null);
            row.set${gc.fkCapField}(parentId);
            mapper.insert(row);
        }
    }
</#list>
</#if>
</#list>
    /**
     * V4.4 按删除级联模式删除主表（模式由生成时配置决定，三选一渲染）：
     * 级联=先删各子表行（含三级）再删主行；阻止=任一子表还有有效行就拒删；不处理=仅删主行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWithChildren(Long id) {
<#if deleteCascadeBlock>
<#list children as child>
        Long ${child.varName}Count = ${child.varName}Mapper.selectCount(
                new LambdaQueryWrapper<${child.className}>()
                        .eq(${child.className}::get${child.fkCapField}, id));
        if (${child.varName}Count != null && ${child.varName}Count > 0) {
            throw new BusinessException("该${comment}下还有 " + ${child.varName}Count + " 条${child.comment}，请先清理明细再删除");
        }
</#list>
        removeById(id);
<#elseif deleteCascadeOn>
<#list children as child>
        // ${child.comment}：先清三级，再按外键逻辑删除本级
<#if child.grandChildren?size gt 0>
        List<${child.className}> ${child.varName}Rows = ${child.varName}Mapper.selectList(
                new LambdaQueryWrapper<${child.className}>()
                        .eq(${child.className}::get${child.fkCapField}, id));
        // 孙层按主键集合一次删：逐行 delete 会让「500 条明细」变成 500 条 SQL，全压在同一个事务里
        List<Long> ${child.varName}Ids = ${child.varName}Rows.stream()
                .map(${child.className}::getId).toList();
<#list child.grandChildren as gc>
        if (!${child.varName}Ids.isEmpty()) {
            ${gc.varName}Mapper.delete(new LambdaQueryWrapper<${gc.className}>()
                    .in(${gc.className}::get${gc.fkCapField}, ${child.varName}Ids));
        }
</#list>
</#if>
        ${child.varName}Mapper.delete(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, id));
</#list>
        removeById(id);
<#else>
        removeById(id);
</#if>
    }
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
