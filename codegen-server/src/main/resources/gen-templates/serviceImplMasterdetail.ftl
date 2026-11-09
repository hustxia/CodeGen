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
<#if genTree>import java.util.ArrayList;
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
