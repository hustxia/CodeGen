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
</#list>
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
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
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
